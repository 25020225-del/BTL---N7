package controller;

import database.dao.BidDAO;
import model.auction.Auction;
import model.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class ServerBidderControllerTest {

    @Mock
    private BidDAO bidDAO;

    @InjectMocks
    private ServerBidderController bidderController;

    @Test
    void placeBid_OnOwnAuction_ShouldRejectImmediately() throws Exception {
        // Arrange
        User seller = new User();
        seller.setId("UET-K70-LOC");

        Auction auction = new Auction();
        auction.setId("AUC-001");
        auction.setSeller(seller);

        // Act: Lộc tự đặt giá vào phiên đấu giá do chính Lộc tạo ra
        CompletableFuture<Boolean> bidResult = bidderController.placeBidOnAuction(seller, auction, 50000L, false);

        // Assert: Tương tác bị từ chối ngay lập tức mà không cần gọi xuống Database
        assertFalse(bidResult.get(), "Hệ thống phải chặn Seller tự bid vào sản phẩm của mình.");
    }
}