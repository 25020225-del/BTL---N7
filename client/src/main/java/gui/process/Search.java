package gui.process;

import info.debatty.java.stringsimilarity.Cosine;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    public static final double MATCH_SCORE = 0.22;

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

    private static String removeAccents(String text) {
        if (text == null) return null;

        // 1. Phân tách các ký tự có dấu thành: Ký tự gốc + Dấu (Ví dụ: á -> a + ´)
        String nfdNormalizedString = Normalizer.normalize(text, Normalizer.Form.NFD);

        // 2. Dùng Regex để loại bỏ tất cả các ký tự thuộc nhóm "Dấu" (Diacritical Marks)
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String result = pattern.matcher(nfdNormalizedString).replaceAll("");

        // 3. Xử lý riêng chữ đ/Đ vì nó không thuộc nhóm ký tự kết hợp dấu ở trên
        return result.replace('đ', 'd').replace('Đ', 'D');
    }
    /**
     * Thực hiện tìm kiếm nâng cao (Fuzzy Search) giữa từ khóa và nội dung văn bản.
     * <p>
     * Quy trình xử lý bao gồm:
     * 1. Loại bỏ dấu tiếng Việt của cả từ khóa và nội dung để tăng độ chính xác.
     * 2. Kiểm tra so khớp trực tiếp (Case-insensitive contains).
     * 3. Nếu không khớp trực tiếp, sử dụng thuật toán Cosine Similarity để tính toán
     * độ tương đồng dựa trên tần suất từ.
     * </p>
     *
     * @param text    Từ khóa tìm kiếm (Keyword) người dùng nhập vào.
     * @param content Nội dung mục tiêu (Topic/Description) cần được kiểm tra.
     * @return {@code true} nếu từ khóa khớp trực tiếp hoặc đạt điểm tương đồng
     * vượt ngưỡng {@code MATCH_SCORE}; {@code false} nếu không có sự tương quan.
     * @see #removeAccents(String)
     * @see info.debatty.java.string.similarity.Cosine
     */
    public static boolean searchText(String text, String content) {
        // Chuyển tiếng việt từ có dấu thành không dấu.
        text = removeAccents(text);
        content = removeAccents(content);
        // Bắt đầu so sánh.
        boolean result = text.toLowerCase().contains(content.toLowerCase());
        if (!result) {
            Cosine cos = new Cosine();
            double score = cos.similarity(content.toLowerCase(), text.toLowerCase());
            if (score > MATCH_SCORE) {
                result = true;
            }
        }
        return result;
    }
}