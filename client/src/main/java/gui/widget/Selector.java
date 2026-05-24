package gui.widget;

import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

/**
 * Composite selection control component combining descriptive text layout labels
 * with structural dropdown option vectors. Dispatches reactive callback hooks upon state mutations.
 */
public class Selector extends HBox {
    private final Label title;
    private final ComboBox<String> combo;
    private Consumer<String> onChange;

    /**
     * Allocates a structured selection widget bound to a dynamic option array.
     *
     * @param titleText the literal text string defining the identity of the selection scope
     * @param choices   the array collection containing the initial option elements
     */
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
     * Extracts the invariant value state representing the active chosen token item.
     *
     * @return the literal string mapping to the selected option, or an empty string if unassigned
     */
    public String getChoice() {
        String value = combo.getValue();
        return value != null ? value : "";
    }

    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }
}