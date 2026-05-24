package gui.process;

import info.debatty.java.stringsimilarity.Cosine;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Text parsing engine companion for layout structures. Houses functional logic
 * for recursive visual tree matching and error-tolerant vector space text matching.
 */
public final class Search {

    public static final double FUZZY_MATCH_THRESHOLD = 0.22;

    private Search() {
    }


    /**
     * Evaluates spatial content string similarities against a search token using
     * accent normalization steps and localized character mappings prior to matrix scoring.
     *
     * @param keyword the target user lookup metric input token
     * @param content the context corpus database payload block to test against
     * @return true if string mapping yields coefficients satisfying the matching threshold
     */
    public static boolean matchesFuzzy(String keyword, String content) {
        if (keyword == null || content == null) return false;

        String normalizedKeyword = removeAccents(keyword).toLowerCase();
        String normalizedContent = removeAccents(content).toLowerCase();

        if (normalizedContent.contains(normalizedKeyword)) {
            return true;
        }

        Cosine cosine = new Cosine();
        double score = cosine.similarity(normalizedContent, normalizedKeyword);
        return score > FUZZY_MATCH_THRESHOLD;
    }

    private static String removeAccents(String text) {
        if (text == null) return null;
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        String stripped = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(nfd).replaceAll("");
        return stripped.replace('đ', 'd').replace('Đ', 'D');
    }
}