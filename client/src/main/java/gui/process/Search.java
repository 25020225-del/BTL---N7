package gui.process;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;

public class Search {
    public static boolean searchText(String text, Node node){
        if (node instanceof Labeled)
            if(((Labeled) node).getText().toLowerCase().contains(text.toLowerCase()))
                return true;
        if (node instanceof Parent)
            for(Node n : ((Parent) node).getChildrenUnmodifiable())
                if(Search.searchText(text, n))
                    return true;
        return false;
    }
}
