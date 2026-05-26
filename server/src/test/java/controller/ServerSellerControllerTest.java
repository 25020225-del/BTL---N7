package controller;

import database.dao.AuctionDAO;
import model.auction.Auction;
import model.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ServerSellerController}.
 * Ensures modifying or deleting auctions complies strictly with current auction states.
 */
@ExtendWith(MockitoExtension.class)
class ServerSellerControllerTest {

    @Mock
    private AuctionDAO auctionDAO;

    @InjectMocks
    private ServerSellerController sellerController;

    @Test
    void deleteAuction_WhenStatusIsRunning_ShouldDenyDeletion() throws Exception {
        User seller = new User();
        seller.setId("UET-K70-KHOA");

        Auction auction = new Auction();
        auction.setId("AUC-002");
        auction.setSeller(seller);
        auction.setStatus(Auction.STATUS_RUNNING);

        boolean result = sellerController.deleteAuction(seller, auction);

        assertFalse(result, "Không được phép xóa phiên đấu giá đang RUNNING.");
        verify(auctionDAO, never()).updateAuctionStatus(anyString(), anyString());
    }

    @Test
    void editAuction_WhenStatusIsFinished_ShouldDenyEdit() throws Exception {
        User seller = new User();
        seller.setId("UET-K70-LONG");

        Auction auction = new Auction();
        auction.setId("AUC-003");
        auction.setSeller(seller);
        auction.setStatus(Auction.STATUS_FINISHED);

        boolean result = sellerController.editAuction(
                seller, auction, "Tên mới", "Mô tả mới", 2000L, null, null, 0
        );

        assertFalse(result, "Không được phép sửa phiên đấu giá đã FINISHED.");
        verify(auctionDAO, never()).updateAuction(any(), anyString(), anyString(), anyLong(), any(), any(), anyInt(), anyString());
    }
}