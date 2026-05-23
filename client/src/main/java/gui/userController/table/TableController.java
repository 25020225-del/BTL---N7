package gui.userController.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import gui.process.Search;
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

public class TableController {
    protected static final Logger log = LoggerFactory.getLogger(TableController.class);
    protected final ObjectMapper mapper = JacksonConfig.mapper();

    protected Consumer<Auction> auctionListener;

    protected Parent tableView;

    @FXML
    protected HBox searchBarContainer;
    @FXML
    protected TilePane mainTilePane;
    @FXML
    protected TextField searchField;
    @FXML
    protected Button searchButton;

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
        searchField.setOnAction(event -> {
            if (searchButton != null) searchButton.fire();
        });
    }

    @FXML
    protected void search() { // FIX: method name cũng nên camelCase nhưng giữ nếu FXML bind "#Search"
        String keyword = searchField.getText();
        for (Node node : mainTilePane.getChildren()) {
            if (node instanceof MinimalItem item) {
                String itemContent = (String) item.getUserData();
                // FIX 1: Đổi tên method từ SearchText → matchesFuzzy
                // FIX 2: Đổi thứ tự tham số: (keyword, content) thay vì (content, keyword)
                boolean match = Search.matchesFuzzy(keyword, itemContent);
                item.setVisible(match);
                item.setManaged(match);
            }
        }
    }

    public void addNewItem(MinimalItem item) {
        mainTilePane.getChildren().addFirst(item);
    }

    public void addAllItem(List<MinimalItem> items) {
        mainTilePane.getChildren().clear();
        for (MinimalItem item : items) {
            mainTilePane.getChildren().add(item);
        }
    }

    public void deleteAllItem() {
        mainTilePane.getChildren().clear();
    }


    public void removeItem(String auctionIdToRemove) {
        mainTilePane.getChildren().removeIf(node -> auctionIdToRemove.equals(node.getId()));
    }


    protected void openItemDetail(Auction auction) {
        auctionListener.accept(auction);
    }


}
