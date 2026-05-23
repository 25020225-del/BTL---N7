package gui.process;

import info.debatty.java.stringsimilarity.Cosine;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * A utility class for performing text-based searches within a JavaFX scene graph
 * and executing fuzzy (approximate) text matching against item content strings.
 *
 * <p>This class is stateless and must not be instantiated.</p>
 */
public final class Search {

    /**
     * The minimum Cosine Similarity score required for a fuzzy match to be considered a hit.
     * A value of {@code 0.22} is empirically calibrated for short item name/description strings.
     */
    public static final double FUZZY_MATCH_THRESHOLD = 0.22; // FIX: renamed from MATCH_SCORE for clarity

    /**
     * Private constructor — utility class, not instantiable.
     */
    private Search() {
    }

    // ── Scene Graph Search ────────────────────────────────────────────────────

    /**
     * Recursively searches for a specific text string within a JavaFX {@link Node} tree.
     * The search is case-insensitive and targets only components implementing {@link Labeled}
     * (e.g., {@code Label}, {@code Button}).
     *
     * @param keyword The target string to search for.
     * @param node    The root JavaFX node to begin the traversal from.
     * @return {@code true} if the keyword is found within the node or any of its descendants.
     */
    public static boolean searchText(String keyword, Node node) { // FIX: param was (text, node) — renamed for clarity
        if (node instanceof Labeled labeled) {
            if (labeled.getText().toLowerCase().contains(keyword.toLowerCase())) {
                return true;
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (searchText(keyword, child)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── Fuzzy Text Matching ───────────────────────────────────────────────────

    /**
     * Performs advanced fuzzy matching between a search keyword and a content string.
     *
     * <p>The matching pipeline is:</p>
     * <ol>
     *     <li>Both strings are stripped of Vietnamese diacritical marks for accent-insensitive comparison.</li>
     *     <li>A direct, case-insensitive substring check is performed first (fast path).</li>
     *     <li>If the direct check fails, Cosine Similarity is computed; a score above
     *         {@link #FUZZY_MATCH_THRESHOLD} is considered a match.</li>
     * </ol>
     *
     * <p><b>FIX (Logic Bug):</b> The original code had the operands of {@code contains()} reversed:
     * it checked if the <em>keyword</em> contained the <em>content</em>, which is backwards.
     * A search for "phone" should match content like "iPhone 15 Pro". Fixed to
     * {@code content.contains(keyword)}.</p>
     *
     * <p><b>FIX (Naming Convention):</b> Renamed from {@code SearchText} (which violated the
     * Java camelCase convention for methods) to {@code matchesFuzzy}.</p>
     *
     * @param keyword The search term entered by the user.
     * @param content The item content string (e.g., name + description) to test against.
     * @return {@code true} if the keyword directly or approximately matches the content.
     */
    public static boolean matchesFuzzy(String keyword, String content) { // FIX: was SearchText(text, content)
        if (keyword == null || content == null) return false;

        String normalizedKeyword = removeAccents(keyword).toLowerCase();
        String normalizedContent = removeAccents(content).toLowerCase();

        // Fast path: direct substring match
        if (normalizedContent.contains(normalizedKeyword)) { // FIX: was text.contains(content) — operands were REVERSED
            return true;
        }

        // Slow path: fuzzy match via Cosine Similarity
        Cosine cosine = new Cosine();
        double score = cosine.similarity(normalizedContent, normalizedKeyword);
        return score > FUZZY_MATCH_THRESHOLD;
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Strips Vietnamese diacritical marks from a string to enable accent-insensitive comparison.
     *
     * @param text The input string, potentially containing accented characters.
     * @return The input string with all diacritical marks removed, or {@code null} if input is {@code null}.
     */
    private static String removeAccents(String text) {
        if (text == null) return null;
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        String stripped = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(nfd).replaceAll("");
        // Handle đ/Đ explicitly as they are not decomposable via NFD
        return stripped.replace('đ', 'd').replace('Đ', 'D');
    }
}
