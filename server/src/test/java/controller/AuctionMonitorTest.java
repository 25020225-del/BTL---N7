package controller;

import database.dao.AuctionDAO;
import database.dao.WalletDAO;
import model.auction.Auction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.ServerExtension.AuctionManager;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuctionMonitor} leveraging Mockito extensions.
 * Validates lifetime lifecycle transitions of auctions stored in memory,
 * ensuring correct status synchronization with the persistence layer.
 */
@ExtendWith(MockitoExtension.class)
class AuctionMonitorTest {

    @Mock
    private AuctionDAO auctionDAO;

    @Mock
    private WalletDAO walletDAO;

    private AuctionMonitor monitor;
    private List<Auction> ramAuctions;

    @BeforeEach
    void setUp() {
        ramAuctions = new ArrayList<>();
        monitor = new AuctionMonitor(ramAuctions, auctionDAO, walletDAO);
        AuctionManager.getAuctionList().clear();
    }

    @Test
    void processRamAuctions_WhenTimeIsUp_ShouldTransitionFromRunningToFinished() throws Exception {
        Auction expiredAuction = new Auction();
        expiredAuction.setId("AUC-EXPIRED-001");
        expiredAuction.setStatus(Auction.STATUS_RUNNING);
        expiredAuction.setStartTime(LocalDateTime.now().minusMinutes(60));
        expiredAuction.setEndTime(LocalDateTime.now().minusMinutes(1));

        AuctionManager.addAuctionToMonitor(expiredAuction);

        when(auctionDAO.updateAuctionStatusEndingIfEndTimeMatches(
                eq("AUC-EXPIRED-001"),
                eq(Auction.STATUS_FINISHED),
                any(LocalDateTime.class)))
                .thenReturn(true);

        Method processMethod = AuctionMonitor.class.getDeclaredMethod("processRamAuctions");
        processMethod.setAccessible(true);
        processMethod.invoke(monitor);

        assertEquals(Auction.STATUS_FINISHED, expiredAuction.getStatus(),
                "Monitor phải tự động chuyển trạng thái RAM sang FINISHED khi hết giờ.");

        verify(auctionDAO, times(1)).updateAuctionStatusEndingIfEndTimeMatches(
                eq("AUC-EXPIRED-001"),
                eq(Auction.STATUS_FINISHED),
                any(LocalDateTime.class));
    }

    @Test
    void processRamAuctions_WhenStillRunning_ShouldNotChangeStatus() throws Exception {
        Auction activeAuction = new Auction();
        activeAuction.setId("AUC-ACTIVE-002");
        activeAuction.setStatus(Auction.STATUS_RUNNING);
        activeAuction.setStartTime(LocalDateTime.now().minusMinutes(10));
        activeAuction.setEndTime(LocalDateTime.now().plusMinutes(30));

        AuctionManager.addAuctionToMonitor(activeAuction);

        Method processMethod = AuctionMonitor.class.getDeclaredMethod("processRamAuctions");
        processMethod.setAccessible(true);
        processMethod.invoke(monitor);

        assertEquals(Auction.STATUS_RUNNING, activeAuction.getStatus(),
                "Monitor không được phép đóng phiên đấu giá khi thời gian vẫn còn.");

        verify(auctionDAO, never()).updateAuctionStatusEndingIfEndTimeMatches(anyString(), anyString(), any());
    }
}