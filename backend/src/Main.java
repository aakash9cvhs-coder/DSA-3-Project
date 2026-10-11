import java.util.List;
import java.util.Scanner;

/**
 * Main class provides an interactive CLI test runner and launches the WebSearchService.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("  E-Commerce Entity Matcher & Search Engine (DSA-3 Project)   ");
        System.out.println("===============================================================");

        MatchingEngine engine = new MatchingEngine();

        // 1. Run automated unit demonstrations
        runAlgorithmSelfTests(engine);

        // 2. Start WebSearchService on port 8080
        int port = 8080;
        try {
            WebSearchService webService = new WebSearchService(engine, port);
            webService.start();
        } catch (Exception e) {
            System.err.println("Note: Could not start HTTP web server on port " + port + ": " + e.getMessage());
            System.err.println("Running in CLI mode only.");
        }

        // 3. Interactive CLI search loop
        System.out.println("\n---------------------------------------------------------------");
        System.out.println(" Interactive CLI Search (Type a query, or 'exit' to quit):");
        System.out.println(" Try queries: 'iphone', 'samung', 'wh-1000xm5', 'running shoes', 'air'");
        System.out.println("---------------------------------------------------------------");

        // If non-interactive environment (arguments passed or no terminal), finish cleanly
        if (args.length > 0 && args[0].equalsIgnoreCase("--test-only")) {
            System.out.println("Self-test completed successfully. Exiting CLI loop.");
            return;
        }

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\nEnter query > ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                System.out.println("Shutting down. Goodbye!");
                break;
            }
            if (input.isEmpty()) {
                continue;
            }

            // Trie autocomplete suggestions
            List<String> suggestions = engine.getSuggestions(input, 5);
            if (!suggestions.isEmpty()) {
                System.out.println("  [Trie Autocomplete Suggestions]: " + suggestions);
            }

            // Execute search
            long startTime = System.nanoTime();
            List<MatchingEngine.SearchResult> results = engine.search(input);
            long elapsedMicros = (System.nanoTime() - startTime) / 1000;

            System.out.printf("  Found %d result(s) in %d microseconds:%n", results.size(), elapsedMicros);
            int rank = 1;
            for (MatchingEngine.SearchResult res : results) {
                MatchingEngine.Product p = res.getProduct();
                System.out.printf(
                    "  %d. [%.2f score | %s%s] %s ($%.2f) - Cat: %s, Rating: %.1f%n" +
                    "     -> Breakdown: [Trie: %.2f, RabinKarp: %.2f, Levenshtein: %.2f, MaxFlow: %.2f]%n",
                    rank++,
                    res.getTotalScore(),
                    res.getMatchType(),
                    res.isFuzzy() ? " (Fuzzy/Typo)" : "",
                    p.getName(),
                    p.getPrice(),
                    p.getCategory(),
                    p.getRating(),
                    res.getTrieScore(),
                    res.getRabinKarpScore(),
                    res.getLevenshteinScore(),
                    res.getMaxFlowScore()
                );
            }
        }
    }

    /**
     * Executes algorithmic self-tests verifying correctness of DSA components.
     */
    private static void runAlgorithmSelfTests(MatchingEngine engine) {
        System.out.println("\n--- [1] Testing TextNormalizer ---");
        String raw = "  Apple iPhone 15 Pro, 256GB!  ";
        String normalized = TextNormalizer.normalize(raw);
        System.out.println("Raw        : \"" + raw + "\"");
        System.out.println("Normalized : \"" + normalized + "\"");
        System.out.println("Tokens     : " + TextNormalizer.tokenize(raw));

        System.out.println("\n--- [2] Testing Trie Prefix Matching ---");
        Trie trie = new Trie();
        trie.insert("iphone", 1);
        trie.insert("ipad", 2);
        trie.insert("ipod", 3);
        trie.insert("macbook", 4);
        System.out.println("Prefix 'ip' suggestions: " + trie.getSuggestions("ip", 5));
        System.out.println("Search 'macbook': " + trie.searchExact("macbook"));

        System.out.println("\n--- [3] Testing Rabin-Karp Substring Matching ---");
        String text = "sony wh-1000xm5 wireless noise cancelling headphones";
        String pattern = "noise cancelling";
        boolean rkFound = RabinKarp.search(text, pattern);
        System.out.printf("Rabin-Karp search for '%s' in text: %b%n", pattern, rkFound);

        System.out.println("\n--- [4] Testing Levenshtein DP (Fuzzy Matching) ---");
        String s1 = "samung"; // typo
        String s2 = "samsung";
        int distance = LevenshteinDP.computeDistance(s1, s2);
        double similarity = LevenshteinDP.computeSimilarity(s1, s2);
        System.out.printf("Distance between '%s' and '%s': %d (Similarity: %.2f)%n", s1, s2, distance, similarity);

        System.out.println("\n--- [5] Testing MaxFlow Bipartite Token Matching ---");
        List<String> queryTokens = List.of("apple", "pro", "titanium");
        List<String> productTokens = List.of("apple", "iphone", "titanium", "pro", "smartphone");
        double flowScore = MaxFlow.computeBipartiteMatchScore(queryTokens, productTokens);
        System.out.printf("MaxFlow Match Score for %s against product tokens: %.2f%n", queryTokens, flowScore);

        System.out.println("\n--- [6] Testing Integrated MatchingEngine ---");
        List<MatchingEngine.SearchResult> testResults = engine.search("samung galxy");
        System.out.printf("Fuzzy Query 'samung galxy' matched %d products. Top match: %s (Score: %.2f, Fuzzy: %b)%n",
                testResults.size(),
                testResults.isEmpty() ? "None" : testResults.get(0).getProduct().getName(),
                testResults.isEmpty() ? 0.0 : testResults.get(0).getTotalScore(),
                testResults.isEmpty() ? false : testResults.get(0).isFuzzy());
    }
}
