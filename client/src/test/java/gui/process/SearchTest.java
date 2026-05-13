package gui.process;

import org.junit.jupiter.api.BeforeEach;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.*;

public class SearchTest {
    private List<Map<String, Object>> auctions;

    @Test
    public void testSearch() {
        String key = "Ga nuong";
        String title = "Ga nuong muoi ot";
        assertEquals(true, Search.searchText(key,title));
    }


    @Test
    public void testSearch2() {
        String key = "Ga nuong";
        String title = "Dui cuu nuong muoi ot";
        assertEquals(true, Search.searchText(key,title));
    }
}