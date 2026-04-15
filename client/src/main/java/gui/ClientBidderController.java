package gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import model.Bidder;
import model.User;

import java.io.File;
import java.io.IOException;

public class ClientBidderController {
    @FXML private VBox mainDock;
    public static void start() throws IOException {
        VBox mainDock = (VBox) MainApplication.rootMainView.lookup("#mainDock");
        mainDock.getChildren().add(0,WidgetFactory.createButton("mdi2f-file-find-otline","","Find"));
        mainDock.getChildren().add(0,WidgetFactory.createButton("mdi2f-file-find-outline","","Find"));
        mainDock.getChildren().add(0,WidgetFactory.createButton("mdi2f-file-find-outline","","Find"));
        mainDock.getChildren().add(WidgetFactory.createButton("mdi2f-file-find-outline","","Find"));
    }
}
