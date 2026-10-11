import java.util.*;

/**
 * Trie (Prefix Tree) implementation from scratch.
 * Efficiently indexes prefixes of product names and search terms
 * to provide autocomplete suggestions and prefix-based product lookup.
 */
public class Trie {

    /**
     * Internal node representing a character in the Trie.
     */
    public static class TrieNode {
        // Map from character to child node (supports full unicode alphanumeric characters)
        Map<Character, TrieNode> children;
        boolean isEndOfWord;
        // Associated product IDs or phrase occurrences ending at or passing through this node
        Set<Integer> productIds;

        public TrieNode() {
            this.children = new HashMap<>();
            this.isEndOfWord = false;
            this.productIds = new HashSet<>();
        }
    }

    private final TrieNode root;

    public Trie() {
        this.root = new TrieNode();
    }

    /**
     * Inserts a word and its associated productId into the Trie.
     *
     * @param word      Word to index
     * @param productId Unique identifier of the product containing this word
     */
    public void insert(String word, int productId) {
        if (word == null || word.isEmpty()) {
            return;
        }
        TrieNode current = root;
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            current.children.putIfAbsent(ch, new TrieNode());
            current = current.children.get(ch);
            current.productIds.add(productId);
        }
        current.isEndOfWord = true;
    }

    /**
     * Checks if a word exists exactly in the Trie.
     *
     * @param word Search term
     * @return True if word exists and is an end of word
     */
    public boolean searchExact(String word) {
        if (word == null || word.isEmpty()) return false;
        TrieNode current = root;
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            if (!current.children.containsKey(ch)) {
                return false;
            }
            current = current.children.get(ch);
        }
        return current.isEndOfWord;
    }

    /**
     * Checks if any indexed term starts with the given prefix.
     *
     * @param prefix Prefix string
     * @return True if prefix is present
     */
    public boolean startsWith(String prefix) {
        return findNode(prefix) != null;
    }

    /**
     * Retrieves all product IDs associated with terms that start with the prefix.
     *
     * @param prefix Prefix string
     * @return Set of matching product IDs
     */
    public Set<Integer> getProductIdsWithPrefix(String prefix) {
        TrieNode node = findNode(prefix);
        if (node == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(node.productIds);
    }

    /**
     * Returns autocomplete keyword suggestions that begin with the given prefix.
     *
     * @param prefix   Prefix to autocomplete
     * @param maxCount Maximum number of suggestions to return
     * @return List of suggested word completions
     */
    public List<String> getSuggestions(String prefix, int maxCount) {
        List<String> results = new ArrayList<>();
        if (prefix == null || prefix.trim().isEmpty()) {
            return results;
        }
        String cleanPrefix = prefix.toLowerCase();
        TrieNode node = findNode(cleanPrefix);
        if (node != null) {
            collectWords(node, new StringBuilder(cleanPrefix), results, maxCount);
        }
        return results;
    }

    private void collectWords(TrieNode node, StringBuilder currentWord, List<String> results, int maxCount) {
        if (results.size() >= maxCount) {
            return;
        }
        if (node.isEndOfWord) {
            results.add(currentWord.toString());
        }
        // Traverse children in deterministic sorted order
        List<Character> keys = new ArrayList<>(node.children.keySet());
        Collections.sort(keys);
        for (char ch : keys) {
            if (results.size() >= maxCount) break;
            currentWord.append(ch);
            collectWords(node.children.get(ch), currentWord, results, maxCount);
            currentWord.deleteCharAt(currentWord.length() - 1);
        }
    }

    private TrieNode findNode(String prefix) {
        if (prefix == null) return null;
        TrieNode current = root;
        for (int i = 0; i < prefix.length(); i++) {
            char ch = prefix.charAt(i);
            if (!current.children.containsKey(ch)) {
                return null;
            }
            current = current.children.get(ch);
        }
        return current;
    }
}
