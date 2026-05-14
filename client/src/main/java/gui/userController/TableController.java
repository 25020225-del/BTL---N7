package gui.userController;

import client.handler.AuctionEventBus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gui.ClientUserController;
import gui.MainApplication;
import gui.process.Search;
import gui.widget.AdminUserItem;
import gui.widget.MinimalItem;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.util.Duration;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
import model.user.User;
import network.NetworkMessage;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TableController {
    private static final Logger log = LoggerFactory.getLogger(ClientUserController.class);
    private final ObjectMapper mapper = JacksonConfig.mapper();

    private Consumer<Auction> auctionListener;

    private Parent tableView;

    @FXML private HBox searchBarContainer;
    @FXML private TilePane mainTilePane;
    @FXML private TextField searchField;
    @FXML private Button searchButton;

    public TableController() {
        FXMLLoader tableViewLoader = new FXMLLoader(getClass().getResource("/gui/TableView.fxml"));
        tableViewLoader.setController(this);
        try {
            tableView = tableViewLoader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public Parent getParent() {
        return tableView;
    }
    public void setOnAuctionListener(Consumer<Auction> auctionListener) {
        this.auctionListener = auctionListener;
    }

    private void setupSearch() {
        searchField.setOnAction(event -> { if (searchButton != null) searchButton.fire(); });
    }

    @FXML
    private void Search(){
        JaroWinklerSimilarity jws = new JaroWinklerSimilarity();
        String keyword = searchField.getText();
        for (Node node : mainTilePane.getChildren()) {
            if (node instanceof MinimalItem item) {
                String itemContent = (String) item.getUserData();
                boolean match = Search.SearchText(itemContent, keyword);
                item.setVisible(match);
                item.setManaged(match);
            }
        }
    }

    public void addNewAuction(Map<String, Object> auction) {
        MinimalItem newItem = buildMinimalItem(auction);
        newItem.setOpacity(0);
        mainTilePane.getChildren().addFirst(newItem);

        FadeTransition ft = new FadeTransition(Duration.millis(500), newItem);
        ft.setToValue(1.0);
        ft.play();
    }

    public void addNewAuction(MinimalItem item) {
        mainTilePane.getChildren().addFirst(item);
    }


    public void addNewUser(AdminUserItem user) {
        mainTilePane.getChildren().addFirst(user);
    }

    public void addAllAuction(List<Map<String, Object>> auctions) {
        mainTilePane.getChildren().clear();
        for(Map<String, Object> auction : auctions) {
            mainTilePane.getChildren().add(buildMinimalItem(auction));
        }
    }

    public void deleteAllAuction(){
        mainTilePane.getChildren().clear();
    }

    public void removeAuction(String auctionIdToRemove) {
        mainTilePane.getChildren().removeIf(node -> auctionIdToRemove.equals(node.getId()));
    }

    private MinimalItem buildMinimalItem(Map<String, Object> map) {
        String id       = (String) map.get("id");
        String name     = (String) map.get("itemName");
        String imageUrl = (String) map.get("imageUrl");
        String sellerId = (String) map.get("sellerId");
        double price    = ((Number) map.get("currentPrice")).doubleValue();
        long endTime    = ((Number) map.get("endTime")).longValue();

        Auction auction = auctionFromMap(map);

        MinimalItem item = new MinimalItem(id, imageUrl, name,
                String.format("%,.0f", price), endTime);
        item.getAuctionButton().setOnAction(e -> openItemDetail(auction));
        return item;
    }

    private void openItemDetail(Auction auction) {
        auctionListener.accept(auction);
    }

    private Auction auctionFromMap(Map<String, Object> map) {
        Auction auction = new Auction();
        auction.setId((String) map.get("id"));

        Item item = ItemFactory.createItem(
                ItemFactory.TYPE_TANGIBLE,
                "ITM-" + map.get("id"),
                (String) map.get("itemName"),
                null,
                0
        );
        item.setImageUrl((String) map.get("imageUrl"));
        auction.setItem(item);

        User seller = new User();
        seller.setId((String) map.get("sellerId"));
        auction.setSeller(seller);

        auction.setCurrentPrice(((Number) map.get("currentPrice")).longValue());
        auction.setEndTime(
                Instant.ofEpochMilli(((Number) map.get("endTime")).longValue())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
        );

        return auction;
    }

    @FXML private void initialize() {
        setupSearch();
        AuctionEventBus.addListener("FETCH_AUCTIONS_SUCCESS", event -> {
            try {
                NetworkMessage response = (NetworkMessage) event.getNewValue();
                List<Map<String, Object>> auctions = mapper.convertValue(
                        response.getData(),
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                addAllAuction(auctions);
            } catch (Exception e) {
                log.error("[Client] FETCH_AUCTIONS_SUCCESS parse error: {}", e.getMessage());
            }
        });

    }
}
