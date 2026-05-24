package gui;

import client.handler.AuctionEventBus;
import client.service.AdminService;
import client.service.AuctionService;
import gui.process.AlertUtils;
import gui.process.RemoveEventBus;
import gui.userController.ItemDetailController;
import gui.userController.table.TableControllerAdmin;
import gui.widget.IconButton;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.auction.Auction;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ClientAdminController {
    private static final Logger log = LoggerFactory.getLogger(ClientAdminController.class);

    private Parent mainView;

    // ✅ Dùng đúng kiểu: SettingsController (extends VBox, tự load FXML của nó)
    private SettingsController settingsView;

    @FXML
    private VBox mainDock;
    @FXML
    private VBox mainViewController;

    private TableControllerAdmin tableView;
    private User currentUser; // ✅ Lưu user để truyền vào SettingsController
    private ItemDetailController currentDetailController;

    private IconButton account;
    private IconButton toggleList = new IconButton("mdi2m-menu", "List", "List", "special-button");
    private IconButton accountList = new IconButton("mdi2a-account-box-multiple-outline", "Accounts", "Manage Accounts", "special-button");
    private IconButton pendingItemList = new IconButton("mdi2a-archive-settings-outline", "Pending Auction", "Pending Auction", "special-button");
    private IconButton runningItemList = new IconButton("mdi2a-archive-settings-outline", "Running Auction", "Running Auction", "special-button");
    private IconButton withdrawList = new IconButton("mdi2c-cash-refund", "Withdrawals", "Withdraw Requests", "special-button");

    public ClientAdminController(User user) throws IOException {
        this.currentUser = user;
        this.account = new IconButton("mdi2a-account", "Admin: " + user.getName(), "Account", "special-button");

        // ── 1. Load MainView.fxml (đúng như cũ, không thay đổi) ──────────
        FXMLLoader mainViewLoader = new FXMLLoader(getClass().getResource("MainView.fxml"));
        mainViewLoader.setController(this);
        mainView = mainViewLoader.load();

        // ── 2. Load TableView ─────────────────────────────────────────────
        tableView = new TableControllerAdmin();
        tableView.setOnAuctionListener((auction) -> openItemDetail(auction));

        // ── 3. ✅ Tạo SettingsController đúng cách ─────────────────────
        //    SettingsController extends VBox và tự xử lý:
        //      loader.setRoot(this) + loader.setController(this) + loader.load()
        //    => Không cần (và không được) load SettingsView.fxml ở đây nữa.
        settingsView = new SettingsController(user);

        // ── 4. Kết nối callback từ Settings về Admin Dashboard ────────────
        settingsView.setOnBackToMarketplace(this::handleBackToMarketplaceInternal);
        settingsView.setOnSignOut(this::handleSignOutInternal);

        // ── 5. Hiển thị màn hình chính ────────────────────────────────────
        MainApplication.setNewScene(mainView);
    }

    /**
     * Cấu hình sidebar navigation dock.
     */
    private void setMainDock() {
        Region region = new Region();
        Separator separator = new Separator();
        VBox.setVgrow(region, Priority.ALWAYS);
        mainDock.getChildren().addAll(
                toggleList,
                accountList,
                pendingItemList,
                runningItemList,
                withdrawList,
                separator,
                region,
                account);

        toggleList.setUserData(true);
        toggleList.setOnAction(event -> {
            for (Node k : mainDock.getChildren()) {
                if (k instanceof Button b) {
                    boolean collapsed = (boolean) toggleList.getUserData();
                    b.setContentDisplay(collapsed ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
                }
            }
            toggleList.setUserData(!((boolean) toggleList.getUserData()));
        });

        pendingItemList.setOnAction(event -> {
            mainViewController.getChildren().setAll(tableView.getParent());
            log.info("Loading pending auctions...");
            AdminService.fetchPendingAuctions();
        });

        runningItemList.setOnAction(event -> {
            mainViewController.getChildren().setAll(tableView.getParent());
            log.info("Loading running auctions...");
            AdminService.fetchRunningAuctions();
        });

        accountList.setOnAction(event -> {
            mainViewController.getChildren().setAll(tableView.getParent());
            log.info("Loading user list...");
            AdminService.fetchUsers();
        });

        // ✅ settingsView là một Node (VBox) hợp lệ, add trực tiếp được
        account.setOnAction(event -> {
            mainViewController.getChildren().setAll(settingsView);
        });

        withdrawList.setOnAction(event -> {
            mainViewController.getChildren().setAll(tableView.getParent());
            log.info("Loading withdraw requests...");
            AdminService.fetchWithdrawRequests();
        });
    }

    private void openItemDetail(Auction auction) {
        if (currentDetailController != null) {
            currentDetailController.dispose();
        }

        ItemDetailController detailController = new ItemDetailController(currentUser);
        detailController.setAuctionData(auction);
        currentDetailController = detailController;
        AuctionService.fetchTransactions(auction.getId());
        detailController.setOnReturnToMarketplace(() -> {
            handleBackToMarketplaceInternal();
        });
        mainViewController.getChildren().clear();
        mainViewController.getChildren().add(detailController.getParent());
    }

    /**
     * Callback được SettingsController gọi khi user bấm "Back to Marketplace".
     * Đây là logic thực sự — không cần @FXML vì không bind từ MainView.fxml.
     */
    private void handleBackToMarketplaceInternal() {
        pendingItemList.fire();
    }

    /**
     * Callback được SettingsController gọi khi user bấm "Đăng xuất".
     */
    private void handleSignOutInternal() {
        RemoveEventBus.forUser();
        RemoveEventBus.forAdmin();
        log.info("Admin is signing out.");
        AdminService.logout();
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    // ── Giữ lại @FXML handlers cho MainView.fxml ──────────────────────────

    @FXML
    private void handleBackToMarketplace() {
        // Được bind từ MainView.fxml nếu có nút nào dùng onAction="#handleBackToMarketplace"
        handleBackToMarketplaceInternal();
    }

    @FXML
    private void handleSignOut() {
        handleSignOutInternal();
    }

    @FXML
    private void handleManageUsers(ActionEvent event) {
        try {
            Parent userView = FXMLLoader.load(getClass().getResource("/gui/UsersManagement.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(userView));
            stage.show();
        } catch (IOException e) {
            log.error("Cannot load UsersManagement.fxml", e);
        }
    }

    private void setMainViewController() {
        AuctionEventBus.addListener("ADMIN_ACTION_SUCCESS", event ->
                Platform.runLater(() ->
                        AlertUtils.showInfo("Success", (String) event.getNewValue())
                )
        );
        AuctionEventBus.addListener(AuctionEventBus.ADMIN_ACTION_SUCCESS, event -> {
            Platform.runLater(() -> {
                AlertUtils.showInfo("Success", event.getNewValue().toString());
                AuctionService.fetchAuctions(); // refresh lại danh sách
            });
        });

        AuctionEventBus.addListener(AuctionEventBus.GENERAL_ERROR, event -> {
            Platform.runLater(() ->
                    AlertUtils.showError("Error", event.getNewValue().toString())
            );
        });
    }

    public void start() {
        setMainDock();
        setMainViewController();
        log.info("Admin view initialized successfully.");
    }
}