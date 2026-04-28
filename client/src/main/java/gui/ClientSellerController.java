package gui;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gui.process.AlertHelper;
import gui.widget.IconButton;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.Auction;
import model.Item;
import model.Seller;
import model.User;

public class ClientSellerController {

    private static ObjectMapper mapper = new ObjectMapper();

    private Parent mainView;
    private Parent sellerCreateAuction;
    private User currentUser;
    private Seller seller;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;

    @FXML private TilePane mainTilePane;

    @FXML private Button createAuction;
    @FXML private TextField sellerCreateAuction_itemName;
    @FXML private TextField sellerCreateAuction_startPrice;
    @FXML private Button chooseFile;
    @FXML private Label fileLinkChoosen;
    @FXML private ImageView sellerCreateAuction_image;
    @FXML private TextArea sellerCreateAuction_descripsion;
    @FXML private DatePicker sellerCreateAuction_startDate;
    @FXML private DatePicker sellerCreateAuction_endDate;
    @FXML private TextField sellerCreateAuction_startMinute;
    @FXML private TextField sellerCreateAuction_startHour;
    @FXML private TextField sellerCreateAuction_endHour;
    @FXML private TextField sellerCreateAuction_endMinute;

    private IconButton account = new IconButton("mdi2a-account", "Hello Seller", "Account", "special-button");
    private IconButton toggleList = new IconButton("mdi2m-menu", "List", "List", "special-button");
    private IconButton createTransaction = new IconButton("mdi2a-archive-plus-outline", "Create Transaction", "Create Transaction", "special-button");

    private Parent loadFx(String location) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(location));
        loader.setController(this);
        return loader.load();
    }

    public ClientSellerController(User user) throws IOException {
        this.currentUser = user;
        this.seller = (Seller) user;
        this.account = new IconButton("mdi2a-account", "Hello, " + user.getName(), "Account");
        mainView = loadFx("MainView.fxml");
        sellerCreateAuction = loadFx("SellerCreateAuction.fxml");
        MainApplication.setNewScene(mainView);
    }
    public void setAutoCropCenter(ImageView imageView, Image image, double destWidth, double destHeight) {
        imageView.setImage(image);
        imageView.setPreserveRatio(false); // Tắt preserve ratio để viewport làm việc
        imageView.setSmooth(true);

        double sourceWidth = image.getWidth();
        double sourceHeight = image.getHeight();

        // Tính toán tỉ lệ để biết nên scale theo chiều nào
        double ratio = Math.max(destWidth / sourceWidth, destHeight / sourceHeight);

        // Tính toán kích thước vùng cắt (Viewport)
        double viewportWidth = destWidth / ratio;
        double viewportHeight = destHeight / ratio;

        // Tính toán tọa độ để lấy ở giữa ảnh gốc
        double viewportX = (sourceWidth - viewportWidth) / 2;
        double viewportY = (sourceHeight - viewportHeight) / 2;

        // Áp dụng vùng cắt
        imageView.setViewport(new Rectangle2D(viewportX, viewportY, viewportWidth, viewportHeight));

        // Ép kích thước hiển thị cuối cùng
        imageView.setFitWidth(destWidth);
        imageView.setFitHeight(destHeight);
    }

    private void setMainDock() {
        mainDock.getChildren().add(account);
        mainDock.getChildren().addFirst(createTransaction);
        mainDock.getChildren().addFirst(toggleList);

        toggleList.setUserData(true);
        toggleList.setOnAction(event -> {
            for(Node k : mainDock.getChildren()) {
                if (k instanceof Button) {
                    Button b = (Button) k;
                    if ((boolean)  toggleList.getUserData()) {
                        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    }
                    else {
                        b.setContentDisplay(ContentDisplay.LEFT);
                    }
                }
            }
            toggleList.setUserData(!((boolean) toggleList.getUserData()));
        });
        createTransaction.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(sellerCreateAuction);
        });
    }
    private void setMainViewController() {
        mainViewController.getChildren().clear();
        chooseFile.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Open Resource File");
            fileChooser.getExtensionFilters().addAll( new  FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg") );
            Stage stage = new Stage();
            stage.setTitle("Open Resource File");
            stage.setResizable(false);
            File file = fileChooser.showOpenDialog(stage);
            fileLinkChoosen.setText(file.getAbsolutePath());
            Image image = new Image(file.toURI().toString());
            setAutoCropCenter(sellerCreateAuction_image,image,320,240);
        });
        createAuction.setOnAction(event -> {
            try {
                // get data from UI
                String itemName = sellerCreateAuction_itemName.getText();
                String description = sellerCreateAuction_descripsion.getText();
                double startPrice = Double.parseDouble(sellerCreateAuction_startPrice.getText());

                // Temp simulating bidIncrement and durationMinutes (MUST be changed later)
                double bidIncrement = startPrice * 0.1;
                int durationMinutes = 60;

                // Data package
                Map<String, String> auctionData = new HashMap<>();
                auctionData.put("itemName", itemName);
                auctionData.put("description", description);
                auctionData.put("startingPrice", String.valueOf(startPrice));
                auctionData.put("bidIncrement", String.valueOf(bidIncrement));
                auctionData.put("durationMinutes", String.valueOf(durationMinutes));

                System.out.println("[Log]: Sending creating auction request for " + itemName + "...");

                // send cmd to server
                MainApplication.networkClient.sendMessage("CREATE_AUCTION", auctionData);

                // delete form after successfully sending
                sellerCreateAuction_itemName.clear();
                sellerCreateAuction_startPrice.clear();
                sellerCreateAuction_descripsion.clear();

            } catch (NumberFormatException e) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Price or time is invalid");
            } catch (Exception e) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Input error: " + e.getMessage());
            }
        });
    }

    public void start() {
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        setMainDock();
        setMainViewController();
    }
}
