import gui.process.Search;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct regression unit tests validating pure stateless fuzzy text matching frameworks.
 * Evaluates capitalization boundaries, token sequencing anomalies, and null safety invariant checks.
 */
@DisplayName("Search.matchesFuzzy() — Fuzzy Text Matching Tests")
class SearchTest {

    @Test
    @DisplayName("Khớp trực tiếp: keyword là substring của content")
    void directSubstringMatch_shouldReturnTrue() {
        assertTrue(Search.matchesFuzzy("phone", "iPhone 15 Pro"));
    }

    @Test
    @DisplayName("Không khớp: keyword không có trong content")
    void noMatch_shouldReturnFalse() {
        assertFalse(Search.matchesFuzzy("nokia", "iPhone 15 Pro"));
    }

    @Test
    @DisplayName("Case-insensitive: khác biệt hoa thường vẫn phải khớp")
    void caseInsensitiveMatch_shouldReturnTrue() {
        assertTrue(Search.matchesFuzzy("IPHONE", "iPhone 15 Pro"));
        assertTrue(Search.matchesFuzzy("pro", "iPHONE 15 PRO"));
    }

    @Test
    @DisplayName("Bỏ dấu tiếng Việt (Diacritics removal): không dấu phải khớp có dấu")
    void diacriticsRemovalMatch_shouldReturnTrue() {
        assertTrue(Search.matchesFuzzy("dien thoai", "Điện thoại Samsung"));
        assertTrue(Search.matchesFuzzy("Dien Thoai", "điện thoại"));
        assertTrue(Search.matchesFuzzy("Điện", "dien thoai samsung"));
    }

    @Test
    @DisplayName("keyword null → trả về false (không throw exception)")
    void nullKeyword_shouldReturnFalse() {
        assertFalse(Search.matchesFuzzy(null, "iPhone 15 Pro"));
    }

    @Test
    @DisplayName("content null → trả về false (không throw exception)")
    void nullContent_shouldReturnFalse() {
        assertFalse(Search.matchesFuzzy("phone", null));
    }

    @Test
    @DisplayName("keyword rỗng → khớp với mọi content")
    void emptyKeyword_shouldMatchAnything() {
        assertTrue(Search.matchesFuzzy("", "iPhone 15 Pro"));
    }

    @ParameterizedTest(name = "[{index}] keyword=''{0}'' trong content=''{1}'' → {2}")
    @CsvSource({
            "apple,   Apple Watch Series 9,  true",
            "samsung, Galaxy S25 Ultra,      false",
            "dong ho, Đồng hồ thông minh,    true",
            "laptop,  Laptop Dell XPS 15,    true",
            "abc,     xyz ijk,               false"
    })
    @DisplayName("Parameterized: nhiều tình huống tìm kiếm tổng hợp")
    void parameterizedFuzzyTests(String keyword, String content, boolean expectedResult) {
        assertEquals(expectedResult, Search.matchesFuzzy(keyword, content));
    }
}