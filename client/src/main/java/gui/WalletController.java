package gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WalletController {

    private Runnable onAuctionCreated;

    Parent walletView;

    @FXML private Label lblTotalBalance;
    @FXML private Label lblFrozenBalance;
    @FXML private TextField txtDepositAmount;

    @FXML private TableView<Transaction> tableTransactions;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, Double> colAmount;
    @FXML private TableColumn<Transaction, String> colStatus;
    @FXML private TableColumn<Transaction, String> colNote;

    private double currentBalance = 0.0;
    private ObservableList<Transaction> transactionData = FXCollections.observableArrayList();

    public WalletController() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("WalletView.fxml"));
        fxmlLoader.setController(this);
        try {
            walletView = fxmlLoader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colNote.setCellValueFactory(new PropertyValueFactory<>("note"));

        tableTransactions.setItems(transactionData);

        transactionData.add(new Transaction("1975-10-20 10:00", "Đặt cọc", 200.0, "Success", "Pinecone : Konichiwa !!"));

        updateBalanceUI();
    }


    public void setOnAuctionCreated(Runnable callback) { // thêm method này
        this.onAuctionCreated = callback;
    }
    public Parent getParent() {
        return walletView;
    }

    @FXML
    private void handleReturn() {
        onAuctionCreated.run();
    }

    @FXML
    private void handleDeposit() {
        try {
            double amount = Double.parseDouble(txtDepositAmount.getText());
            if (amount <= 0) throw new Exception();

            currentBalance += amount;


            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            transactionData.add(0, new Transaction(now, "Nạp tiền", amount, "Completed", "Nạp tiền qua ngân hàng"));

            updateBalanceUI();
            txtDepositAmount.clear();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thành công");
            alert.setHeaderText(null);
            alert.setContentText("Đã nạp $" + amount );
            alert.showAndWait();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Vui lòng nhập số tiền hợp lệ!");
            alert.showAndWait();
        }
    }

    private void updateBalanceUI() {
        lblTotalBalance.setText(String.format("$%.2f", currentBalance));
    }
    // nap nhanh
    @FXML
    private void addQuickAmount(javafx.event.ActionEvent event) {
        Button btn = (Button) event.getSource();
        String text = btn.getText().replace("+$", "");
        txtDepositAmount.setText(text);
    }
}