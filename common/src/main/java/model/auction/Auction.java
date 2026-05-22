package model.auction;

import model.base.Entity;
import model.finance.BidTransaction;
import model.item.Item;
import model.user.User;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

import static utils.ConsoleColors.*;

/**
 * Represents an auction session within the system.
 *
 * <h2>Vòng đời phiên đấu giá (Life-cycle)</h2>
 * <pre>
 *   PENDING_APPROVAL
 *        │  (admin duyệt)
 *        ▼
 *      OPEN  ──(startTime đến)──▶  WAITING_FOR_BID
 *                                       │
 *                               (bid hợp lệ đầu tiên)
 *                                       │ end_time = now + durationMinutes
 *                                       ▼
 *                                   RUNNING  ──(endTime đến)──▶  FINISHED
 *                                                                     │
 *                                                              (thanh toán)
 *                                                            ┌────────┴────────┐
 *                                                          PAID           CANCELED
 * </pre>
 *
 * <h2>Thay đổi so với phiên bản cũ</h2>
 * <ul>
 *   <li>Thêm trạng thái {@link #STATUS_WAITING_FOR_BID}: phiên đã khai mạc nhưng
 *       đồng hồ chưa chạy — chờ bid đầu tiên.</li>
 *   <li>Xoá {@code maxEndTime} (hard-cap 30 phút). Anti-sniping gia hạn <em>vô hạn lần</em>.</li>
 *   <li>Thêm trường {@code durationMinutes}: thời lượng gốc, dùng để tính {@code endTime}
 *       tại thời điểm bid đầu tiên.</li>
 *   <li>{@code endTime} có thể là {@code null} khi {@code status = WAITING_FOR_BID}.</li>
 * </ul>
 */
public class Auction extends Entity {

    // -------------------------------------------------------------------------
    // Trạng thái vòng đời
    // -------------------------------------------------------------------------

    /** Chờ admin duyệt. */
    public static final String STATUS_PENDING = "PENDING_APPROVAL";

    /**
     * Đã duyệt, chưa đến giờ khai mạc.
     * {@code endTime} được tính sẵn nhưng <em>chưa có ý nghĩa</em> — đồng hồ chưa chạy.
     */
    public static final String STATUS_OPEN = "OPEN";

    /**
     * Đã đến giờ khai mạc, <strong>đang chờ bid đầu tiên</strong>.
     * {@code endTime} = {@code null} — đồng hồ chưa chạy.
     * Chuyển sang {@link #STATUS_RUNNING} ngay khi có bid hợp lệ đầu tiên.
     */
    public static final String STATUS_WAITING_FOR_BID = "WAITING_FOR_BID";

    /**
     * Đang chạy, đã có ít nhất một bid.
     * {@code endTime} đã được xác lập = thời điểm bid đầu + {@code durationMinutes}.
     */
    public static final String STATUS_RUNNING = "RUNNING";

    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_PAID     = "PAID";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_DELETED  = "DELETED";

    // -------------------------------------------------------------------------
    // Hằng số Anti-Sniping
    // -------------------------------------------------------------------------

    /** Gia hạn khi có bid trong X giây cuối. */
    public static final long ANTI_SNIPING_THRESHOLD_SECONDS = 60;

    /**
     * Số giây cộng thêm mỗi lần gia hạn (2 phút).
     * Không có giới hạn số lần — gia hạn vô hạn khi cần.
     */
    public static final long ANTI_SNIPING_EXTENSION_SECONDS = 120;

    // -------------------------------------------------------------------------
    // Trường dữ liệu
    // -------------------------------------------------------------------------

    private Item item;
    private User seller;

    private long currentPrice;
    private long highestMaxBid;
    private long bidIncrement;

    private User winningBidder;
    private String status;

    private LocalDateTime startTime;

    /**
     * Thời điểm kết thúc phiên.
     * <p><strong>Quan trọng:</strong> Trường này là {@code null} khi
     * {@code status = WAITING_FOR_BID}. Mọi đoạn code đọc {@code endTime}
     * phải kiểm tra null hoặc kiểm tra status trước.</p>
     */
    private LocalDateTime endTime;

    /**
     * Thời lượng gốc của phiên (phút), được lưu vào DB cột {@code duration_minutes}.
     * Dùng để tính {@code endTime = now + durationMinutes} tại thời điểm bid đầu tiên.
     */
    private int durationMinutes;

    private List<BidTransaction> bidHistory = new ArrayList<>();

    /** Queue thread-safe xử lý AutoBid theo thứ tự thời gian đăng ký. */
    private PriorityBlockingQueue<AutoBid> activeAutoBids;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Constructor mặc định (dùng cho Jackson deserialize và DAO mapping).
     */
    public Auction() {
        super();
        this.activeAutoBids = new PriorityBlockingQueue<>(
                11, Comparator.comparing(AutoBid::getTimeRegistered));
    }

    /**
     * Constructor đầy đủ — dùng khi load auction từ DB hoặc tạo mới.
     *
     * @param id              ID duy nhất của phiên.
     * @param item            Tài sản đấu giá.
     * @param seller          Người bán.
     * @param bidIncrement    Mức tăng tối thiểu mỗi lần bid.
     * @param startTime       Thời điểm khai mạc.
     * @param endTime         Thời điểm kết thúc ({@code null} nếu WAITING_FOR_BID).
     * @param durationMinutes Thời lượng gốc (phút).
     */
    public Auction(String id, Item item, User seller,
                   long bidIncrement,
                   LocalDateTime startTime, LocalDateTime endTime,
                   int durationMinutes) {
        super(id);
        this.item          = item;
        this.seller        = seller;
        this.currentPrice  = item.getStartingPrice();
        this.highestMaxBid = 0;
        this.bidIncrement  = bidIncrement;
        this.startTime     = startTime;
        this.endTime       = endTime;         // null khi WAITING_FOR_BID
        this.durationMinutes = durationMinutes;
        this.bidHistory    = new ArrayList<>();
        this.activeAutoBids = new PriorityBlockingQueue<>(
                11, Comparator.comparing(AutoBid::getTimeRegistered));

        this.status = seller.isGood() ? STATUS_OPEN : STATUS_PENDING;
    }

    /**
     * Constructor backward-compatible (6 tham số, không có durationMinutes).
     * Tự tính {@code durationMinutes} từ khoảng cách startTime → endTime.
     *
     * @deprecated Dùng constructor 7 tham số để lưu đúng {@code durationMinutes}.
     */
    @Deprecated
    public Auction(String id, Item item, User seller,
                   long bidIncrement,
                   LocalDateTime startTime, LocalDateTime endTime) {
        this(id, item, seller, bidIncrement, startTime, endTime,
                (endTime != null && startTime != null)
                        ? (int) java.time.Duration.between(startTime, endTime).toMinutes()
                        : 60);
    }

    // -------------------------------------------------------------------------
    // Factory method
    // -------------------------------------------------------------------------

    /**
     * Tạo một phiên đấu giá mới với cơ chế "chờ bid đầu tiên".
     *
     * <p>Khác với phiên bản cũ, {@code endTime} <strong>không</strong> được tính ngay.
     * {@code endTime} sẽ chỉ được xác lập tại thời điểm bid đầu tiên được đặt thành công.</p>
     *
     * @param item            Tài sản đấu giá.
     * @param seller          Người bán.
     * @param bidIncrement    Mức tăng tối thiểu.
     * @param startTime       Thời điểm khai mạc phiên.
     * @param durationMinutes Thời lượng phiên (tính từ bid đầu tiên).
     * @return Auction mới, {@code endTime = null}, status = OPEN hoặc PENDING_APPROVAL.
     */
    public static Auction createNewAuction(Item item, User seller,
                                           long bidIncrement,
                                           LocalDateTime startTime,
                                           int durationMinutes) {
        String newId = "AUC-" + System.currentTimeMillis();

        // endTime = null: đồng hồ chưa chạy, chờ bid đầu tiên
        Auction newAuction = new Auction(
                newId, item, seller,
                bidIncrement,
                startTime, null,   // <-- endTime = null
                durationMinutes);

        newAuction.setStatus(seller.isGood() ? STATUS_OPEN : STATUS_PENDING);
        return newAuction;
    }

    // -------------------------------------------------------------------------
    // Inner class: BidResult
    // -------------------------------------------------------------------------

    /**
     * Kết quả tính toán của một lần bid trước khi được áp dụng.
     * Immutable; dùng trong pattern calculate → DB commit → apply.
     */
    public static class BidResult {
        public final User          newWinner;
        public final long          newHighestMaxBid;
        public final long          newCurrentPrice;
        public final LocalDateTime newEndTime;

        /**
         * Cờ đánh dấu đây là bid đầu tiên của phiên (WAITING_FOR_BID → RUNNING).
         * Khi {@code true}: caller phải cập nhật {@code status = RUNNING} trong DB.
         */
        public final boolean isFirstBid;

        public BidResult(User newWinner, long newHighestMaxBid,
                         long newCurrentPrice, LocalDateTime newEndTime,
                         boolean isFirstBid) {
            this.newWinner        = newWinner;
            this.newHighestMaxBid = newHighestMaxBid;
            this.newCurrentPrice  = newCurrentPrice;
            this.newEndTime       = newEndTime;
            this.isFirstBid       = isFirstBid;
        }
    }

    // -------------------------------------------------------------------------
    // Business Logic: calculateBidResult
    // -------------------------------------------------------------------------

    /**
     * Tính toán kết quả đặt giá mà <strong>không thay đổi trạng thái</strong> auction.
     *
     * <h3>Các trạng thái được chấp nhận:</h3>
     * <ul>
     *   <li>{@link #STATUS_WAITING_FOR_BID} – bid đầu tiên; {@code endTime} sẽ được
     *       tính = {@code now + durationMinutes} và trả về trong {@code BidResult}.</li>
     *   <li>{@link #STATUS_RUNNING} – bid bình thường; anti-sniping không có hard-cap.</li>
     * </ul>
     *
     * @param bidder    Người đặt giá.
     * @param newMaxBid Mức giá tối đa người dùng sẵn sàng trả.
     * @return {@link BidResult} với kết quả tính toán, hoặc {@code null} nếu bid không hợp lệ.
     */
    public BidResult calculateBidResult(User bidder, long newMaxBid) {
        boolean isWaiting = STATUS_WAITING_FOR_BID.equals(status);
        boolean isRunning = STATUS_RUNNING.equals(status);

        // Chỉ chấp nhận hai trạng thái này
        if (!isWaiting && !isRunning) {
            return null;
        }

        // Nếu đang RUNNING: kiểm tra endTime chưa qua
        if (isRunning) {
            if (endTime == null || LocalDateTime.now().isAfter(endTime)) {
                return null;
            }
        }

        // ── Kiểm tra mức bid tối thiểu ──────────────────────────────────────
        long minRequiredBid = (winningBidder == null)
                ? currentPrice
                : (currentPrice + bidIncrement);

        if (newMaxBid < minRequiredBid) {
            return null;
        }

        // ── Tính toán winner & price mới ────────────────────────────────────
        User nextWinner       = winningBidder;
        long nextHighestMaxBid = highestMaxBid;
        long nextCurrentPrice  = currentPrice;

        if (winningBidder == null) {
            // Bid đầu tiên (trạng thái WAITING_FOR_BID hoặc RUNNING chưa có winner)
            nextCurrentPrice   = item.getStartingPrice();
            nextHighestMaxBid  = newMaxBid;
            nextWinner         = bidder;

        } else if (bidder.getId().equals(winningBidder.getId())) {
            // Người đang thắng nâng mức đặt của chính họ
            if (newMaxBid > highestMaxBid) {
                nextHighestMaxBid = newMaxBid;
            }

        } else {
            // Người mới cố vượt qua người đang thắng
            if (newMaxBid > highestMaxBid) {
                nextCurrentPrice = highestMaxBid + bidIncrement;
                if (nextCurrentPrice > newMaxBid) {
                    nextCurrentPrice = newMaxBid;
                }
                nextHighestMaxBid = newMaxBid;
                nextWinner        = bidder;
            } else {
                nextCurrentPrice = newMaxBid + bidIncrement;
                if (nextCurrentPrice > highestMaxBid) {
                    nextCurrentPrice = highestMaxBid;
                }
            }
        }

        // ── Tính toán endTime mới ────────────────────────────────────────────
        LocalDateTime nextEndTime;

        if (isWaiting) {
            // Bid đầu tiên: kích hoạt đồng hồ ngay bây giờ
            nextEndTime = LocalDateTime.now().plusMinutes(durationMinutes);
            return new BidResult(nextWinner, nextHighestMaxBid, nextCurrentPrice,
                    nextEndTime, true /* isFirstBid */);
        }

        // RUNNING: kiểm tra anti-sniping
        nextEndTime = endTime;
        LocalDateTime now = LocalDateTime.now();
        long secondsRemaining = Duration.between(now, endTime).getSeconds();

        if (secondsRemaining > 0 && secondsRemaining <= ANTI_SNIPING_THRESHOLD_SECONDS) {
            // Gia hạn vô hạn lần — KHÔNG có hard-cap
            nextEndTime = endTime.plusSeconds(ANTI_SNIPING_EXTENSION_SECONDS);
        }

        return new BidResult(nextWinner, nextHighestMaxBid, nextCurrentPrice,
                nextEndTime, false /* isFirstBid */);
    }

    // -------------------------------------------------------------------------
    // Business Logic: applyBidResult
    // -------------------------------------------------------------------------

    /**
     * Áp dụng kết quả bid đã tính ({@link BidResult}) vào trạng thái in-memory.
     *
     * <p>Gọi phương thức này <strong>sau khi</strong> DB transaction đã commit thành công.
     * Nếu {@code result.isFirstBid == true}, trạng thái sẽ tự động chuyển sang
     * {@link #STATUS_RUNNING}.</p>
     *
     * @param bidder Người đặt giá.
     * @param result Kết quả đã tính bởi {@link #calculateBidResult}.
     * @return {@link BidTransaction} vừa tạo.
     */
    public BidTransaction applyBidResult(User bidder, BidResult result) {
        this.winningBidder    = result.newWinner;
        this.highestMaxBid    = result.newHighestMaxBid;
        this.currentPrice     = result.newCurrentPrice;
        this.endTime          = result.newEndTime;

        // Chuyển trạng thái khi bid đầu tiên kích hoạt đồng hồ
        if (result.isFirstBid) {
            this.status = STATUS_RUNNING;
        }

        BidTransaction transaction = new BidTransaction(
                "TXN-" + System.currentTimeMillis(), bidder, currentPrice);
        bidHistory.add(transaction);
        return transaction;
    }

    // -------------------------------------------------------------------------
    // Business Logic: closeAuctionIfTimeIsUp
    // -------------------------------------------------------------------------

    /**
     * Kiểm tra thời gian và chuyển trạng thái sang {@link #STATUS_FINISHED} nếu đến giờ.
     *
     * <p>Phiên ở trạng thái {@link #STATUS_WAITING_FOR_BID} sẽ không bao giờ bị đóng
     * bởi phương thức này vì {@code endTime == null}.</p>
     */
    public void closeAuctionIfTimeIsUp() {
        // WAITING_FOR_BID: endTime = null, không đóng
        if (endTime == null) {
            return;
        }
        if (STATUS_RUNNING.equals(this.status) && LocalDateTime.now().isAfter(this.endTime)) {
            this.status = STATUS_FINISHED;
        }
    }

    // -------------------------------------------------------------------------
    // Business Logic: revertLastBid
    // -------------------------------------------------------------------------

    /**
     * Hoàn nguyên một giao dịch bid thất bại khỏi in-memory state.
     * Thường dùng khi DB transaction rollback sau khi đã gọi {@link #applyBidResult}.
     *
     * @param previousWinner        Winner trước khi bid thất bại.
     * @param previousHighestMaxBid Mức maxBid trước khi bid thất bại.
     * @param previousEndTime       endTime trước khi bid thất bại (có thể null).
     * @param previousStatus        Status trước khi bid thất bại.
     * @param failedTransaction     Giao dịch cần xoá khỏi bidHistory.
     */
    public void revertLastBid(User previousWinner, long previousHighestMaxBid,
                              LocalDateTime previousEndTime, String previousStatus,
                              BidTransaction failedTransaction) {
        if (failedTransaction != null) {
            bidHistory.remove(failedTransaction);
        }
        this.winningBidder    = previousWinner;
        this.highestMaxBid    = previousHighestMaxBid;
        this.endTime          = previousEndTime;
        this.status           = previousStatus;

        if (bidHistory.isEmpty()) {
            this.currentPrice = item.getStartingPrice();
        } else {
            this.currentPrice = bidHistory.get(bidHistory.size() - 1).getBidAmount();
        }
    }

    // -------------------------------------------------------------------------
    // Business Logic: registerAutoBid
    // -------------------------------------------------------------------------

    /**
     * Đăng ký AutoBid bot cho người dùng.
     * Chấp nhận cả khi phiên đang {@link #STATUS_WAITING_FOR_BID} để
     * user có thể đặt sẵn bot trước khi phiên thực sự bắt đầu đếm.
     *
     * @param bidder        Người dùng.
     * @param maxBid        Mức tối đa.
     * @param userIncrement Bước tăng giá.
     * @return {@code true} nếu đăng ký thành công.
     */
    public boolean registerAutoBid(User bidder, long maxBid, long userIncrement) {
        boolean isActive = STATUS_RUNNING.equals(status)
                || STATUS_WAITING_FOR_BID.equals(status);
        if (!isActive) {
            return false;
        }
        if (maxBid <= currentPrice) {
            return false;
        }
        activeAutoBids.offer(new AutoBid(bidder, maxBid, userIncrement));
        return true;
    }

    // -------------------------------------------------------------------------
    // Deprecated: placeBid (legacy single-step bid)
    // -------------------------------------------------------------------------

    /**
     * @deprecated Dùng {@link #calculateBidResult} + {@link #applyBidResult} để đồng bộ
     *     DB-RAM một cách atomic. Phương thức này giữ lại để backward-compat với test cũ.
     */
    @Deprecated
    public BidTransaction placeBid(User bidder, long newMaxBid) {
        boolean canBid = STATUS_WAITING_FOR_BID.equals(status) || STATUS_RUNNING.equals(status);
        if (!canBid) return null;
        if (STATUS_RUNNING.equals(status) && (endTime == null || LocalDateTime.now().isAfter(endTime))) {
            return null;
        }
        if (newMaxBid < 0) return null;

        long minRequired = (winningBidder == null) ? currentPrice : (currentPrice + bidIncrement);
        if (newMaxBid < minRequired) return null;

        BidResult result = calculateBidResult(bidder, newMaxBid);
        if (result == null) return null;
        return applyBidResult(bidder, result);
    }

    // -------------------------------------------------------------------------
    // getInfo
    // -------------------------------------------------------------------------

    @Override
    public String getInfo() {
        return "=== AUCTION INFORMATION ===\n"
                + "Auction ID   : " + this.getId() + "\n"
                + "Item         : " + (item != null ? item.getItemName() : "N/A") + "\n"
                + "Current Price: VND " + this.currentPrice + "\n"
                + "Leading Bidder: " + (winningBidder != null ? winningBidder.getUserName() : "None") + "\n"
                + "Status       : " + this.status + "\n"
                + "End Time     : " + (endTime != null ? endTime : "Chờ bid đầu tiên…") + "\n"
                + "Duration     : " + this.durationMinutes + " phút";
    }

    // =========================================================================
    // GETTERS & SETTERS
    // =========================================================================

    public Item getItem()                         { return item; }
    public void setItem(Item item)                { this.item = item; }

    public User getSeller()                       { return seller; }
    public void setSeller(User seller)            { this.seller = seller; }

    public long getCurrentPrice()                 { return currentPrice; }
    public void setCurrentPrice(long currentPrice){ this.currentPrice = currentPrice; }

    public long getHighestMaxBid()                { return highestMaxBid; }
    public void setHighestMaxBid(long highestMaxBid){ this.highestMaxBid = highestMaxBid; }

    public long getBidIncrement()                 { return bidIncrement; }
    public void setBidIncrement(long bidIncrement){ this.bidIncrement = bidIncrement; }

    public User getWinningBidder()                { return winningBidder; }
    public void setWinningBidder(User winningBidder){ this.winningBidder = winningBidder; }

    public String getStatus()                     { return status; }
    public void setStatus(String status)          { this.status = status; }

    public LocalDateTime getStartTime()           { return startTime; }
    public void setStartTime(LocalDateTime startTime){ this.startTime = startTime; }

    /**
     * Trả về thời điểm kết thúc. <strong>Có thể null</strong> khi
     * {@code status = WAITING_FOR_BID}.
     */
    public LocalDateTime getEndTime()             { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public int getDurationMinutes()               { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes){ this.durationMinutes = durationMinutes; }

    public List<BidTransaction> getBidHistory()   { return bidHistory; }
    public void setBidHistory(List<BidTransaction> bidHistory){ this.bidHistory = bidHistory; }

    public PriorityBlockingQueue<AutoBid> getActiveAutoBids(){ return activeAutoBids; }

}