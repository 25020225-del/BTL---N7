package gui.widget;

import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

public class Selector extends HBox {
    private Label title;
    private ComboBox<String> combo;
    private Consumer<String> onChange;

    public Selector(String titleText, String... choices) {
        this.title = new Label(titleText);

        this.combo = new ComboBox<>();
        if (choices != null && choices.length > 0) {
            this.combo.getItems().addAll(choices);
            this.combo.setValue(choices[0]);
        }

        this.setSpacing(10);
        this.setAlignment(Pos.CENTER_LEFT);

        this.getChildren().addAll(this.title, this.combo);

        combo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (onChange != null) {
                onChange.accept(newValue);
            }
        });
    }

    /**
     * Choose current choice
     */
    public String getChoice() {
        String value = combo.getValue();
        return value != null ? value : "";
    }

    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }
}