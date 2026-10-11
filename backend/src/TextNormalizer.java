import java.util.*;

/**
 * TextNormalizer handles cleaning and standardizing raw text.
 * It removes special characters, extra whitespace, and converts to lowercase
 * to enable consistent string matching across all algorithms.
 */
public class TextNormalizer {

    /**
     * Normalizes a string by converting to lowercase, removing non-alphanumeric
     * characters (except spaces), and trimming duplicate spaces.
     *
     * @param input Raw input string
     * @return Cleaned, normalized string
     */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        // Convert to lowercase
        String lower = input.toLowerCase();
        
        // Keep only alphanumeric characters and spaces
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isLetterOrDigit(ch) || Character.isWhitespace(ch)) {
                cleaned.append(ch);
            } else {
                // Replace punctuation with space so words don't get joined
                cleaned.append(' ');
            }
        }
        
        // Tokenize and collapse whitespace
        StringTokenizer tokenizer = new StringTokenizer(cleaned.toString());
        StringBuilder result = new StringBuilder();
        while (tokenizer.hasMoreTokens()) {
            result.append(tokenizer.nextToken());
            if (tokenizer.hasMoreTokens()) {
                result.append(" ");
            }
        }
        return result.toString();
    }

    /**
     * Splits normalized text into distinct word tokens.
     *
     * @param input Raw or normalized input string
     * @return List of word tokens
     */
    public static List<String> tokenize(String input) {
        String normalized = normalize(input);
        List<String> tokens = new ArrayList<>();
        if (normalized.isEmpty()) {
            return tokens;
        }
        StringTokenizer tokenizer = new StringTokenizer(normalized);
        while (tokenizer.hasMoreTokens()) {
            tokens.add(tokenizer.nextToken());
        }
        return tokens;
    }
}
