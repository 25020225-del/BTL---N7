package gui.userController;

import client.handler.AuctionEventBus;
import client.network.NetworkService;
import gui.MainApplication;
import gui.Transaction;
import gui.process.AlertHelper;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.LongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox; // Import đúng layout gốc
import javafx.util.Duration;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;


/**
 * WalletController acts as both a Controller and a Custom Node (VBox).
 */
public class WalletController extends VBox {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    private Runnable onReturnAction;


    @FXML private Label lblTotalBalance;
    @FXML private Label lblFrozenBalance;
    @FXML private TextField txtDepositAmount;

    @FXML private TableView<Transaction> tableTransactions;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, Long> colAmount;
    @FXML private TableColumn<Transaction, String> colStatus;
    @FXML private TableColumn<Transaction, String> colNote;

    @FXML private Button btnDeposit;

    private long currentBalance = 0L;
    private ObservableList<Transaction> transactionData = FXCollections.observableArrayList();

    public WalletController() {
        // Load FXML as a Custom Control
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/gui/WalletView.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load WalletView.fxml", e);
        }
    }

    @FXML
    public void initialize() {
        // Initialize table columns
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colNote.setCellValueFactory(new PropertyValueFactory<>("note"));

        AuctionEventBus.addListener("FETCH_WALLET_SUCCESS",event -> {
            NetworkMessage response = (NetworkMessage) event.getNewValue();
            Map<String,Object> map = (Map<String, Object>) response.getData();
            long balance = Long.parseLong(map.get("balance").toString());
            log.info("Get wallet balence success: {}", balance);
            Platform.runLater(() -> {setWalletBalance(balance);});
        });

        tableTransactions.setItems(transactionData);
        updateBalanceUI();
        NetworkService.sendMessage("FETCH_WALLET","");
    }

    public void setOnReturnAction(Runnable action) {
        this.onReturnAction = action;
    }

    public void setWalletBalance(long balance){
        currentBalance = balance;
        lblTotalBalance.setText(String.valueOf(currentBalance)+" N VND");
    }

    @FXML
    private void handleReturn() {
        if (onReturnAction != null) {
            onReturnAction.run();
        }
    }

    @FXML
    private void handleDeposit() {
        String input = txtDepositAmount.getText().trim();
        double amount;
        try {
            amount = Double.parseDouble(input);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Lỗi", "Vui lòng nhập số tiền hợp lệ!");
            return;
        }
        btnDeposit.setDisable(true);
        PauseTransition pauseTransition = new PauseTransition(Duration.seconds(2));
        pauseTransition.setOnFinished(event -> {
            btnDeposit.setDisable(false);
        });
        pauseTransition.play();

        NetworkService.sendMessage("CREATE_DEPOSIT", amount);
    }

    private void updateBalanceUI() {
        lblTotalBalance.setText(String.format("%d N VND", currentBalance));
        lblFrozenBalance.setText("0 N VND");
    }


    @FXML
    private void addQuickAmount(javafx.event.ActionEvent event) {
        Button btn = (Button) event.getSource();
        String text = btn.getText().replace("+", "").replace("k", "000").replace("M", "000000");
        txtDepositAmount.setText(text);
    }
}