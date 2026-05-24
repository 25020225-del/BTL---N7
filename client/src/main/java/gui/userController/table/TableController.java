package gui.userController.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import gui.process.AnimateEffect;
import gui.process.Search;
import gui.widget.Selector;
import gui.widget.item.MinimalItem;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import model.auction.Auction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base abstract presentation controller for grid-based layout structures.
 * Manages foundational scene graph mutations, directional keyword query lookups,
 * and child component spatial distribution filters.
 */
public class TableController {
    protected static final Logger log = LoggerFactory.getLogger(TableController.class);
    protected final ObjectMapper mapper = JacksonConfig.mapper();

    protected Consumer<Auction> auctionListener;
    protected Parent tableView;

    @FXML protected HBox searchBarContainer;
    @FXML protected TilePane mainTilePane;
    @FXML protected TextField searchField;
    @FXML protected Button searchButton;
    @FXML protected HBox fillerBarContainer;

    /**
     * Instantiates the composite view structure and anchors this controller context to the FXML lifecycle.
     */
    public TableController() {
        FXMLLoader tableViewLoader = new FXMLLoader(getClass().getResource("/gui/TableView.fxml"));
        tableViewLoader.setController(this);
        try {
            tableView = tableViewLoader.load();
        } catch (IOException e) {
            throw new RuntimeException("Critical failure loading visual grid layout resource hierarchy", e);
        }
    }

    public Parent getParent() {
        return tableView;
    }

    public void setOnAuctionListener(Consumer<Auction> auctionListener) {
        this.auctionListener = auctionListener;
    }

    protected void setupSearch() {
        searchField.setOnAction(event -> {
            if (searchButton != null) searchButton.fire();
        });
    }

    @FXML
    protected void search() {
        String keyword = searchField.getText();
        for (Node node : mainTilePane.getChildren()) {
            if (node instanceof MinimalItem item) {
                String itemContent = (String) item.getUserData();
                boolean match = Search.matchesFuzzy(keyword, itemContent);
                if (match) {
                    AnimateEffect.showNode(item);
                } else {
                    AnimateEffect.hideNode(item);
                }
            }
        }
    }

    public void addSelector(Selector selector) {
        fillerBarContainer.getChildren().add(selector);
    }

    public void removeAllSelectors() {
        fillerBarContainer.getChildren().clear();
    }

    /**
     * Filters visibility attributes across the child viewport collections matching criteria tokens.
     */
    public void searchByProperties(String key, String value) {
        for (Node node : mainTilePane.getChildren()) {
            if (value.trim().isEmpty()) {
                AnimateEffect.showNode(node);
                continue;
            }
            AnimateEffect.hideNode(node);
            if (node instanceof MinimalItem) {
                if (!node.getProperties().containsKey(key)) continue;
                if (value.equals(node.getProperties().get(key))) {
                    AnimateEffect.showNode(node);
                }
            }
        }
    }

    public void addNewItem(MinimalItem item) {
        mainTilePane.getChildren().addFirst(item);
    }

    public void addAllItem(List<MinimalItem> items) {
        mainTilePane.getChildren().clear();
        mainTilePane.getChildren().addAll(items);
    }

    public void deleteAllItem() {
        mainTilePane.getChildren().clear();
    }

    public void removeItem(String auctionIdToRemove) {
        mainTilePane.getChildren().removeIf(node -> auctionIdToRemove.equals(node.getId()));
    }

    protected void openItemDetail(Auction auction) {
        if (auctionListener != null) {
            auctionListener.accept(auction);
        }
    }
}