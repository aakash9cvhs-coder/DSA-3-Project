import java.util.*;

/**
 * Rabin-Karp algorithm for rolling hash substring search.
 * Computes polynomial rolling hash values to find exact occurrences of query patterns
 * in target product texts in O(N + M) average time complexity.
 */
public class RabinKarp {

    // Prime modulus for rolling hash to minimize collisions
    private static final long PRIME_MOD = 1_000_000_007L;
    // Base multiplier (covering standard ASCII characters)
    private static final int BASE = 256;

    /**
     * Checks whether the pattern exists as an exact substring in the text using Rabin-Karp.
     *
     * @param text    The source text (e.g., product title or description)
     * @param pattern The query pattern to search for
     * @return true if pattern appears at least once in text
     */
    public static boolean search(String text, String pattern) {
        if (text == null || pattern == null) return false;
        if (pattern.isEmpty()) return true;
        if (text.length() < pattern.length()) return false;

        return !searchAllOccurrences(text, pattern).isEmpty();
    }

    /**
     * Finds all starting indices of pattern in text using Rabin-Karp rolling hash.
     *
     * @param text    Source string
     * @param pattern Pattern to locate
     * @return List of 0-based start indices where pattern matches text
     */
    public static List<Integer> searchAllOccurrences(String text, String pattern) {
        List<Integer> matches = new ArrayList<>();
        if (text == null || pattern == null) return matches;
        int n = text.length();
        int m = pattern.length();
        if (m == 0 || n < m) return matches;

        // Calculate highest power: BASE^(m-1) % PRIME_MOD
        long h = 1;
        for (int i = 0; i < m - 1; i++) {
            h = (h * BASE) % PRIME_MOD;
        }

        long patternHash = 0;
        long textHash = 0;

        // Calculate initial hash values for pattern and the first window of text
        for (int i = 0; i < m; i++) {
            patternHash = (patternHash * BASE + pattern.charAt(i)) % PRIME_MOD;
            textHash = (textHash * BASE + text.charAt(i)) % PRIME_MOD;
        }

        // Slide the pattern over text one by one
        for (int i = 0; i <= n - m; i++) {
            // Check hash match
            if (patternHash == textHash) {
                // Hash match confirmed, now verify character by character to handle collisions
                boolean identical = true;
                for (int j = 0; j < m; j++) {
                    if (text.charAt(i + j) != pattern.charAt(j)) {
                        identical = false;
                        break;
                    }
                }
                if (identical) {
                    matches.add(i);
                }
            }

            // Calculate rolling hash value for next window
            if (i < n - m) {
                long leading = (text.charAt(i) * h) % PRIME_MOD;
                textHash = (textHash - leading + PRIME_MOD) % PRIME_MOD;
                textHash = (textHash * BASE + text.charAt(i + m)) % PRIME_MOD;
            }
        }

        return matches;
    }

    /**
     * Calculates an exact substring match score between query and product text.
     * Rewards exact full matches, start-of-string matches, and multiple occurrences.
     *
     * @param text Normalized product text
     * @param pattern Normalized query pattern
     * @return Score between 0.0 and 1.0
     */
    public static double computeMatchScore(String text, String pattern) {
        if (text == null || pattern == null || pattern.isEmpty() || text.isEmpty()) {
            return 0.0;
        }
        if (text.equals(pattern)) {
            return 1.0; // Perfect full match
        }
        List<Integer> occurrences = searchAllOccurrences(text, pattern);
        if (occurrences.isEmpty()) {
            return 0.0;
        }
        // Base score proportional to pattern length coverage
        double coverage = (double) pattern.length() / (double) text.length();
        double score = 0.5 + (0.3 * coverage);
        // Bonus if found at the beginning of the text
        if (occurrences.contains(0)) {
            score += 0.2;
        }
        return Math.min(1.0, score);
    }
}
