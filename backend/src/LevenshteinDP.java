/**
 * Levenshtein Distance using 2D Dynamic Programming.
 * Measures the minimum number of single-character edits (insertions, deletions, or substitutions)
 * required to transform one string into another. Used for typo-tolerant fuzzy matching.
 */
public class LevenshteinDP {

    /**
     * Computes the Levenshtein edit distance between string s1 and string s2.
     * Uses bottom-up 2D Dynamic Programming table:
     * dp[i][j] represents distance between s1[0...i-1] and s2[0...j-1].
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Minimum edit distance (int >= 0)
     */
    public static int computeDistance(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null || s1.isEmpty()) return s2 == null ? 0 : s2.length();
        if (s2 == null || s2.isEmpty()) return s1.length();

        int len1 = s1.length();
        int len2 = s2.length();

        // dp[i][j] stores the edit distance between s1[0..i-1] and s2[0..j-1]
        int[][] dp = new int[len1 + 1][len2 + 1];

        // Base cases: transforming empty string into prefix of other string
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        // Fill DP table
        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);

                if (c1 == c2) {
                    // No edit required for matching characters
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    // Cost of substitution, insertion, or deletion
                    int substitution = dp[i - 1][j - 1] + 1;
                    int insertion = dp[i][j - 1] + 1;
                    int deletion = dp[i - 1][j] + 1;

                    dp[i][j] = Math.min(substitution, Math.min(insertion, deletion));
                }
            }
        }

        return dp[len1][len2];
    }

    /**
     * Computes normalized similarity score between 0.0 (completely dissimilar) and 1.0 (identical).
     * Formula: 1.0 - (editDistance / max(len1, len2))
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Similarity score between 0.0 and 1.0
     */
    public static double computeSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;

        int dist = computeDistance(s1, s2);
        double similarity = 1.0 - ((double) dist / (double) maxLen);
        return Math.max(0.0, Math.min(1.0, similarity));
    }

    /**
     * Finds the best fuzzy word match for a token within a multi-word target string.
     * E.g., matching "aple" against "apple iphone 15 pro max" returns high similarity with "apple".
     *
     * @param queryToken Single query word
     * @param targetText Full target string
     * @return Highest similarity score against any individual word in targetText
     */
    public static double bestWordSimilarity(String queryToken, String targetText) {
        if (queryToken == null || targetText == null || queryToken.isEmpty() || targetText.isEmpty()) {
            return 0.0;
        }
        String[] words = targetText.split("\\s+");
        double best = 0.0;
        for (String word : words) {
            double sim = computeSimilarity(queryToken, word);
            if (sim > best) {
                best = sim;
            }
        }
        return best;
    }
}
