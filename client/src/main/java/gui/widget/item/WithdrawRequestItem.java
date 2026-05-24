package gui.widget.item;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Presentation component representing a financial ledger payout request node.
 * Binds unstructured data schemas onto uniform visualization elements and
 * wraps decision signaling pathways into tokenized command contracts.
 */
public class WithdrawRequestItem extends VBox {

    /**
     * Constructs a withdrawal resolution container card. Extracts property parameters
     * from the raw data transport matrix matching strict wire key identities.
     *
     * @param data     the data transfer mapping block containing the keys: {@code id}, {@code username},
     * {@code amount}, {@code payoutMethod}, and optionally {@code payoutDetails}
     * @param onAction the transactional event relay consumer enforcing a tokenized {@code ACTION:ID} string contract
     */
    public WithdrawRequestItem(Map<String, Object> data, Consumer<String> onAction) {
        String requestId = (String) data.get("id");
        String username = (String) data.get("username");
        long amount = ((Number) data.get("amount")).longValue();
        String method = (String) data.get("payoutMethod");
        String details = (String) data.getOrDefault("payoutDetails", "—");

        String formatted = NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(amount) + " VND";

        Label lblUser = new Label("Người dùng: " + username);
        Label lblAmount = new Label("Số tiền: " + formatted);
        Label lblMethod = new Label("Phương thức: " + method);
        Label lblDetails = new Label("Thông tin: " + details);

        Button btnApprove = new Button("✔ Duyệt");
        Button btnReject = new Button("✘ Từ chối");

        btnApprove.setOnAction(e -> onAction.accept("APPROVE:" + requestId));
        btnReject.setOnAction(e -> onAction.accept("REJECT:" + requestId));

        btnApprove.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        btnReject.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");

        HBox buttons = new HBox(10, btnApprove, btnReject);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        this.setSpacing(6);
        this.setPadding(new Insets(12));
        this.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #ddd; -fx-border-radius: 8;");
        this.getChildren().addAll(lblUser, lblAmount, lblMethod, lblDetails, buttons);
    }
}