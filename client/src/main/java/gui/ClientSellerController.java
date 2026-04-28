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

public class ClientSellerController {

    private static ObjectMapper mapper = new ObjectMapper();

    private Parent mainView;
    private Parent sellerCreateAuction;

    Seller seller = new Seller();

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

    public ClientSellerController() throws IOException {
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
                String itemName = sellerCreateAuction_itemName.getText();
                String descripsion = sellerCreateAuction_descripsion.getText();
                double startPrice = Double.parseDouble(sellerCreateAuction_startPrice.getText());
                Item item = new Item(UUID.randomUUID().toString(),itemName,descripsion,startPrice);
                int startHour = Integer.parseInt(sellerCreateAuction_startHour.getText());
                int  endHour = Integer.parseInt(sellerCreateAuction_endHour.getText());
                int startMinute = Integer.parseInt(sellerCreateAuction_startMinute.getText());
                int endMinute = Integer.parseInt(sellerCreateAuction_endMinute.getText());
                LocalDateTime startTime = sellerCreateAuction_startDate.getValue().atTime(startHour,startMinute,0);
                LocalDateTime endTime = sellerCreateAuction_endDate.getValue().atTime(endHour,endMinute,0);
                Auction auction = new Auction(UUID.randomUUID().toString(),item,seller,50,startTime,endTime);
                String data = mapper.writeValueAsString(auction);
                System.out.println(data);
            } catch (Exception e) {
                AlertHelper.showAlert(Alert.AlertType.ERROR,"Error","Lỗi nhập liệu");
                //throw  new RuntimeException(e);
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

    public void createAuction() {
        Map<String, String> auctionData = new HashMap<>();
        auctionData.put("itemName", "Lông dái Ronaldo");
        auctionData.put("description", "Còn thơm mùi nước đái");
        auctionData.put("startingPrice", "2500000000");
        auctionData.put("bidIncrement", "5000000");
        auctionData.put("durationMinutes", "69");

        System.out.println("[Log]: Sending creating auction request...");
        MainApplication.networkClient.sendMessage("CREATE_AUCTION", auctionData);
    }
}
