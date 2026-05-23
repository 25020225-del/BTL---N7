import gui.process.Search;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Tests cho Search.matchesFuzzy().
 * <p>
 * Class Search là pure utility (stateless) → không cần Mock gì cả.
 * Đây là loại test dễ nhất và có giá trị cao nhất sau Refactor.
 * <p>
 * ĐẶT FILE NÀY Ở: client/src/test/java/gui/process/SearchTest.java
 * <p>
 * SAU KHI REFACTOR — những thay đổi cần biết:
 * - Tên cũ: SearchText(String text, String content)  → tên mới: matchesFuzzy(String keyword, String content)
 * - Thứ tự tham số cũ: (content, keyword)            → mới: (keyword, content)
 * - Logic bug đã sửa: content.contains(keyword) thay vì keyword.contains(content) (reversed)
 */
@DisplayName("Search.matchesFuzzy() — Fuzzy Text Matching Tests")
class SearchTest {

    // ─── 1. Direct substring match (fast path) ────────────────────────────────

    @Test
    @DisplayName("Khớp trực tiếp: keyword là substring của content")
    void directSubstringMatch_shouldReturnTrue() {
        // "phone" nằm trong "iPhone 15 Pro"
        assertTrue(Search.matchesFuzzy("phone", "iPhone 15 Pro"));
    }

    @Test
    @DisplayName("Khớp không phân biệt hoa/thường")
    void caseInsensitiveMatch_shouldReturnTrue() {
        assertTrue(Search.matchesFuzzy("IPHONE", "iPhone 15 Pro"));
        assertTrue(Search.matchesFuzzy("iphone", "IPHONE 15 PRO"));
    }

    @Test
    @DisplayName("Không khớp khi content không chứa keyword")
    void noMatch_shouldReturnFalse() {
        assertFalse(Search.matchesFuzzy("laptop", "iPhone 15 Pro"));
    }

    // ─── 2. BUG REGRESSION TEST — lỗi logic đảo ngược operand ────────────────
    // Đây là test quan trọng nhất: xác nhận bug cũ đã được sửa.
    // Bug cũ: keyword.contains(content) → sai hoàn toàn về chiều so sánh
    // Fix mới: content.contains(keyword) → đúng

    @Test
    @DisplayName("[REGRESSION] content.contains(keyword) — không phải keyword.contains(content)")
    void regression_searchDirection_contentShouldContainKeyword() {
        // "phone" (5 ký tự) KHÔNG chứa "iPhone 15 Pro" (13 ký tự) → keyword.contains(content) = false
        // "iPhone 15 Pro" (13 ký tự) CHỨA "phone" (5 ký tự) → content.contains(keyword) = true
        // Test này sẽ FAIL nếu logic bị đảo lại về bug cũ.
        assertTrue(Search.matchesFuzzy("phone", "iPhone 15 Pro"),
                "REGRESSION: content phải contain keyword, không phải ngược lại!");
    }

    // ─── 3. Fuzzy match (slow path — Cosine Similarity) ─────────────────────

    @Test
    @DisplayName("Fuzzy match: typo nhỏ vẫn khớp được")
    void fuzzyMatch_withTypo_shouldReturnTrue() {
        // "iphon" gần giống "iphone" → Cosine similarity > 0.22
        assertTrue(Search.matchesFuzzy("iphon", "iphone 15 pro"));
    }

    @Test
    @DisplayName("Fuzzy match: keyword và content hoàn toàn khác nhau → không khớp")
    void fuzzyMatch_totallyDifferent_shouldReturnFalse() {
        assertFalse(Search.matchesFuzzy("xyz123", "iPhone Apple Watch"));
    }

    // ─── 4. Vietnamese accent-insensitive ────────────────────────────────────

    @Test
    @DisplayName("Tìm kiếm không dấu khớp với nội dung có dấu")
    void accentInsensitiveSearch_shouldMatch() {
        // "dien thoai" (không dấu) phải khớp với "Điện thoại" (có dấu)
        assertTrue(Search.matchesFuzzy("dien thoai", "Điện thoại Samsung"));
    }

    @Test
    @DisplayName("Tìm kiếm có dấu khớp với nội dung không dấu")
    void accentSearch_matchesNonAccentContent() {
        assertTrue(Search.matchesFuzzy("Điện", "dien thoai samsung"));
    }

    // ─── 5. Edge cases ───────────────────────────────────────────────────────

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
    @DisplayName("keyword rỗng → khớp với mọi content (empty string là substring của bất kỳ string nào)")
    void emptyKeyword_shouldMatchAnything() {
        assertTrue(Search.matchesFuzzy("", "iPhone 15 Pro"));
    }

    // ─── 6. Parameterized test — nhiều cases cùng lúc ────────────────────────

    @ParameterizedTest(name = "[{index}] keyword=''{0}'' trong content=''{1}'' → {2}")
    @CsvSource({
            "apple,   Apple Watch Series 9,  true",
            "samsung, Galaxy S25 Ultra,      false",
            "dong ho, Đồng hồ thông minh,    true",
            "laptop,  Laptop Dell XPS 15,    true",
            "abc,     xyz ijk,               false"
    })
    @DisplayName("Parameterized: nhiều tình huống tìm kiếm")
    void parameterizedSearch(String keyword, String content, boolean expected) {
        assertEquals(expected, Search.matchesFuzzy(keyword.trim(), content.trim()));
    }
}