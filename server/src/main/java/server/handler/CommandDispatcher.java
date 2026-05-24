package server.handler;

import exception.AuctionExceptions;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Core command routing matrix implementation utilizing the Command structural design pattern.
 * Decentralizes global ingress socket traffic directly into contextual सिंगल handlers.
 */
public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);
    private final Map<String, CommandHandler> handlers = new HashMap<>();

    private final database.dao.UserDAO userDAO;
    private final database.dao.AuctionDAO auctionDAO;
    private final database.dao.BidDAO bidDAO;
    private final database.dao.WalletDAO walletDAO;
    private final service.TOTPService totpService;
    private final controller.ServerSellerController sellerCtrl;
    private final controller.ServerPaymentController paymentCtrl;
    private final database.dao.WithdrawalDAO withdrawalDAO;

    public CommandDispatcher(
            database.dao.UserDAO userDAO,
            database.dao.AuctionDAO auctionDAO,
            database.dao.BidDAO bidDAO,
            database.dao.WalletDAO walletDAO,
            database.dao.WithdrawalDAO withdrawalDAO,
            service.TOTPService totpService,
            controller.ServerSellerController sellerCtrl,
            controller.ServerPaymentController paymentCtrl) {

        this.userDAO = userDAO;
        this.auctionDAO = auctionDAO;
        this.bidDAO = bidDAO;
        this.walletDAO = walletDAO;
        this.totpService = totpService;
        this.sellerCtrl = sellerCtrl;
        this.paymentCtrl = paymentCtrl;
        this.withdrawalDAO = withdrawalDAO;

        registerHandlers();
    }

    private void registerHandlers() {
        service.AdminAuctionService adminAuctionService = new service.AdminAuctionService(auctionDAO, walletDAO);

        SystemHandler sysHandler = new SystemHandler();
        handlers.put("PING", sysHandler);
        handlers.put("TIME_SYNC", sysHandler);

        AuthHandler authHandler = new AuthHandler();
        handlers.put("LOGIN", authHandler);
        handlers.put("REGISTER", authHandler);
        handlers.put("LOGOUT", authHandler);
        handlers.put("VERIFY_2FA", authHandler);
        handlers.put("REQUEST_SETUP_2FA", authHandler);
        handlers.put("CANCEL_2FA_SETUP", authHandler);
        handlers.put("VERIFY_2FA_SETUP", authHandler);
        handlers.put("CONFIRM_SETUP_2FA", authHandler);
        handlers.put("DISABLE_2FA", authHandler);
        handlers.put("UPDATE_TOTP_PREFS", authHandler);

        AuctionActionHandler auctionHandler = new AuctionActionHandler(sellerCtrl);
        handlers.put("CREATE_AUCTION", auctionHandler);

        PaymentHandler paymentHandler = new PaymentHandler(paymentCtrl, totpService, walletDAO);
        handlers.put("CREATE_DEPOSIT", paymentHandler);
        handlers.put("CONFIRM_DEPOSIT", paymentHandler);
        handlers.put("FETCH_WALLET", paymentHandler);
        handlers.put("REQUEST_WITHDRAW", paymentHandler);

        FetchAuctionsHandler fetchHandler = new FetchAuctionsHandler(auctionDAO);
        handlers.put("FETCH_AUCTIONS", fetchHandler);
        handlers.put("FETCH_PENDING_AUCTIONS", fetchHandler);
        handlers.put("FETCH_MY_AUCTIONS", fetchHandler);

        controller.ServerAdminController adminCtrl = new controller.ServerAdminController(userDAO, auctionDAO, walletDAO, withdrawalDAO);
        AdminActionHandler adminHandler = new AdminActionHandler(auctionDAO, userDAO, adminCtrl, adminAuctionService);
        handlers.put("APPROVE_AUCTION", adminHandler);
        handlers.put("REJECT_AUCTION", adminHandler);
        handlers.put("FETCH_USERS", adminHandler);
        handlers.put("BLOCK_USER", adminHandler);
        handlers.put("UNBLOCK_USER", adminHandler);
        handlers.put("TOGGLE_GOOD_STATUS", adminHandler);

        BidActionHandler bidHandler = new BidActionHandler(new controller.ServerBidderController(bidDAO), auctionDAO);
        handlers.put("PLACE_BID", bidHandler);
        handlers.put("SETUP_AUTOBID", bidHandler);

        FetchTransactionHandler fetchTransactionHandler = new FetchTransactionHandler(bidDAO);
        handlers.put("FETCH_TRANSACTIONS", fetchTransactionHandler);

        SellerActionHandler sellerHandler = new SellerActionHandler(sellerCtrl, auctionDAO);
        handlers.put("EDIT_AUCTION", sellerHandler);
        handlers.put("DELETE_AUCTION", sellerHandler);

        handlers.put("FETCH_WITHDRAW_REQUESTS", adminHandler);
        handlers.put("APPROVE_WITHDRAW", adminHandler);
        handlers.put("REJECT_WITHDRAW", adminHandler);
        handlers.put("CANCEL_AUCTION", adminHandler);

        AuctionRoomHandler roomHandler = new AuctionRoomHandler();
        handlers.put("JOIN_AUCTION", roomHandler);
        handlers.put("LEAVE_AUCTION", roomHandler);
    }

    /**
     * Decodes and directs structural transport envelopes into their designated runtime handlers.
     *
     * @param message inbound transport packet element
     * @param client  target context session channel source
     */
    public void dispatch(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();
        CommandHandler handler = handlers.get(command);

        if (handler != null) {
            try {
                handler.handle(message, client);

            } catch (AuctionExceptions.AuctionBaseException baseEx) {
                log.warn("Business logic violation [{}]: {}", baseEx.getErrorCode(), baseEx.getMessage());
                network.ErrorPayload errorData = new network.ErrorPayload(baseEx.getErrorCode(), baseEx.getMessage());
                client.sendResponse("ERROR", errorData);

            } catch (com.fasterxml.jackson.core.JsonProcessingException jsonEx) {
                log.warn("Invalid JSON format from {}: {}", client.getClientName(), jsonEx.getMessage());
                network.ErrorPayload errorData = new network.ErrorPayload("ERR_PAYLOAD_001", "Dữ liệu gửi lên không đúng định dạng.");
                client.sendResponse("ERROR", errorData);

            } catch (Exception e) {
                log.error("CRITICAL FATAL ERROR executing command {}: ", command, e);
                network.ErrorPayload errorData = new network.ErrorPayload("ERR_SYS_500", "Lỗi hệ thống máy chủ nội bộ. Vui lòng thử lại sau.");
                client.sendResponse("ERROR", errorData);
            }
        } else {
            log.warn("Unrecognized command: {}", command);
            client.sendResponse("ERROR", new network.ErrorPayload("ERR_SYS_404", "Unrecognized command"));
        }
    }
}