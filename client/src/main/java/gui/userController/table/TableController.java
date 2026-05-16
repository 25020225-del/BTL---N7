package gui.userController.table;

import client.handler.AuctionEventBus;
import client.service.AuctionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gui.process.AlertHelper;
import gui.process.Search;
import gui.widget.AdminUserItem;
import gui.widget.item.MinimalItem;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
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
    protected static final Logger log = LoggerFactory.getLogger(TableController.class);
    protected final ObjectMapper mapper = JacksonConfig.mapper();

    protected Consumer<Auction> auctionListener;

    protected Parent tableView;

    @FXML protected HBox searchBarContainer;
    @FXML protected TilePane mainTilePane;
    @FXML protected TextField searchField;
    @FXML protected Button searchButton;

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

    protected void setupSearch() {
        searchField.setOnAction(event -> { if (searchButton != null) searchButton.fire(); });
    }

    @FXML protected void Search(){
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

    public void addNewAuction(MinimalItem item) {
        mainTilePane.getChildren().addFirst(item);
    }

    public void addAllAuction(List<MinimalItem>  items) {
        mainTilePane.getChildren().clear();
        for(MinimalItem item : items) {
            mainTilePane.getChildren().add(item);
        }
    }

    public void deleteAllAuction(){
        mainTilePane.getChildren().clear();
    }

    public void removeAuction(String auctionIdToRemove) {
        mainTilePane.getChildren().removeIf(node -> auctionIdToRemove.equals(node.getId()));
    }



    protected void openItemDetail(Auction auction) {
        auctionListener.accept(auction);
    }


}
