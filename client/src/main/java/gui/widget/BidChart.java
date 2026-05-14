package gui.widget;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class BidChart extends VBox {
    private Label bid = new Label("Bid history (Real time)");
    private XYChart.Series<String, Number> series = new XYChart.Series<>();
    private ObservableList<XYChart.Data<String, Number>> data = FXCollections.observableArrayList();
    private final int MAX_POINT = 20;
    public BidChart() {
    }
}
