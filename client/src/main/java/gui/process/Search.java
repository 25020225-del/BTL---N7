package gui.process;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;

/**
 * A utility class for performing text-based searches within a JavaFX scene graph.
 */
public class Search {

    /**
     * Recursively searches for a specific text string within a JavaFX {@link Node} and its children.
     * The search is case-insensitive and specifically targets components that implement {@link Labeled}
     * (e.g., Labels, Buttons).
     *
     * @param text The target string to search for.
     * @param node The root JavaFX Node to begin the search from.
     * @return {@code true} if the text is found within the node or any of its descendants; {@code false} otherwise.
     */
    public static boolean searchText(String text, Node node) {
        if (node instanceof Labeled)
            if (((Labeled) node).getText().toLowerCase().contains(text.toLowerCase()))
                return true;
        if (node instanceof Parent)
            for (Node n : ((Parent) node).getChildrenUnmodifiable())
                if (Search.searchText(text, n))
                    return true;
        return false;
    }
}