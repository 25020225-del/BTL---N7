package gui.process;

// Thêm 2 dòng import này:
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

public class SearchTest {
    private List<Map<String, Object>> auctions;

    @Test
    public void testSearch() {
        String key = "Ga nuong";
        String title = "Ga nuong muoi ot";
        // JUnit 5 khuyên dùng: assertEquals(expected, actual)
        assertEquals(true, Search.searchText(key, title));
    }

    @Test
    public void testSearch2() {
        String key = "Ga nuong";
        String title = "Dui cuu nuong muoi ot";
        assertEquals(true, Search.searchText(key, title));
    }
}