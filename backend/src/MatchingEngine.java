import java.util.*;

/**
 * MatchingEngine combines all core DSA components:
 * 1. TextNormalizer: query and entity sanitization.
 * 2. Trie: prefix matching & autocomplete suggestions.
 * 3. Rabin-Karp: exact pattern & substring identification.
 * 4. LevenshteinDP: fuzzy matching & typo tolerance.
 * 5. MaxFlow: multi-attribute bipartite assignment optimization.
 *
 * Produces comprehensive relevance scores and ranked lists of matching products.
 */
public class MatchingEngine {

    /**
     * Product entity representation in the e-commerce catalog.
     */
    public static class Product {
        private final int id;
        private final String name;
        private final String category;
        private final double price;
        private final double rating;
        private final String description;
        private final List<String> tags;

        private final String thumbnail;

        public Product(int id, String name, String category, double price, double rating, String description, List<String> tags) {
            this(id, name, category, price, rating, description, tags, "");
        }

        public Product(int id, String name, String category, double price, double rating, String description, List<String> tags, String thumbnail) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.price = price;
            this.rating = rating;
            this.description = description;
            this.tags = tags != null ? tags : new ArrayList<>();
            this.thumbnail = thumbnail != null ? thumbnail : "";
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public double getPrice() { return price; }
        public double getRating() { return rating; }
        public String getDescription() { return description; }
        public List<String> getTags() { return tags; }
        public String getThumbnail() { return thumbnail; }

        public String getCombinedSearchText() {
            return name + " " + category + " " + String.join(" ", tags) + " " + description;
        }
    }

    /**
     * Match result containing the product, computed algorithmic scores, and match type.
     */
    public static class SearchResult implements Comparable<SearchResult> {
        private final Product product;
        private final double totalScore;
        private final double trieScore;
        private final double rabinKarpScore;
        private final double levenshteinScore;
        private final double maxFlowScore;
        private final boolean isFuzzy;
        private final String matchType; // "Exact", "Prefix", "Fuzzy", "Attribute Flow"

        public SearchResult(Product product, double totalScore, double trieScore, double rabinKarpScore,
                            double levenshteinScore, double maxFlowScore, boolean isFuzzy, String matchType) {
            this.product = product;
            this.totalScore = totalScore;
            this.trieScore = trieScore;
            this.rabinKarpScore = rabinKarpScore;
            this.levenshteinScore = levenshteinScore;
            this.maxFlowScore = maxFlowScore;
            this.isFuzzy = isFuzzy;
            this.matchType = matchType;
        }

        public Product getProduct() { return product; }
        public double getTotalScore() { return totalScore; }
        public double getTrieScore() { return trieScore; }
        public double getRabinKarpScore() { return rabinKarpScore; }
        public double getLevenshteinScore() { return levenshteinScore; }
        public double getMaxFlowScore() { return maxFlowScore; }
        public boolean isFuzzy() { return isFuzzy; }
        public String getMatchType() { return matchType; }

        @Override
        public int compareTo(SearchResult other) {
            // Descending order of total score
            int scoreComparison = Double.compare(other.totalScore, this.totalScore);
            if (scoreComparison != 0) {
                return scoreComparison;
            }
            // Secondary tie-breaker: rating
            return Double.compare(other.product.getRating(), this.product.getRating());
        }
    }

    private final List<Product> catalog;
    private final Trie prefixTrie;

    public MatchingEngine() {
        this.catalog = new ArrayList<>();
        this.prefixTrie = new Trie();
        loadDefaultProducts();
    }

    /**
     * Loads a realistic e-commerce product catalog.
     */
    private void loadDefaultProducts() {
        addProduct(new Product(16, "Apple iPhone 16", "Smartphones", 79900.00, 4.8,
                "Next-generation iPhone with A18 chip, Camera Control, 48MP Fusion camera and Action button",
                Arrays.asList("apple", "iphone", "iphone 16", "base", "ios", "phone", "5g", "a18")));

        addProduct(new Product(17, "Apple iPhone 15", "Smartphones", 69900.00, 4.7,
                "Dynamic Island smartphone with 48MP main camera and A16 Bionic chip",
                Arrays.asList("apple", "iphone", "iphone 15", "base", "ios", "phone", "5g")));

        addProduct(new Product(1, "Apple iPhone 15 Pro", "Smartphones", 134900.00, 4.8,
                "Titanium smartphone with A17 Pro chip and Super Retina XDR display",
                Arrays.asList("apple", "iphone", "iphone 15", "15 pro", "ios", "titanium", "phone", "5g")));

        addProduct(new Product(13, "Apple iPhone 16 Pro Max", "Smartphones", 144900.00, 4.9,
                "Flagship titanium smartphone with A18 Pro chip, 48MP camera fusion and Camera Control",
                Arrays.asList("apple", "iphone", "iphone 16", "16 pro max", "pro max", "ios", "titanium", "flagship", "phone", "5g")));

        addProduct(new Product(14, "Apple iPhone 16 Pro", "Smartphones", 119900.00, 4.8,
                "Pro smartphone with A18 Pro chip, Grade 5 Titanium design and 120Hz ProMotion",
                Arrays.asList("apple", "iphone", "iphone 16", "16 pro", "pro", "ios", "titanium", "phone", "5g")));

        addProduct(new Product(15, "Apple iPhone 15 Pro Max", "Smartphones", 149900.00, 4.8,
                "Titanium smartphone with 5x telephoto optical zoom and A17 Pro chip",
                Arrays.asList("apple", "iphone", "iphone 15", "15 pro max", "pro max", "ios", "titanium", "phone", "5g")));

        addProduct(new Product(2, "Samsung Galaxy S24 Ultra", "Smartphones", 129999.00, 4.7,
                "AI powered smartphone with 200MP camera and embedded S Pen",
                Arrays.asList("samsung", "galaxy", "android", "ai", "spen", "phone")));

        addProduct(new Product(3, "Sony WH-1000XM5 Wireless Headphones", "Audio", 29990.00, 4.6,
                "Industry leading noise cancelling wireless over-ear headphones",
                Arrays.asList("sony", "headphones", "bluetooth", "wireless", "noise cancelling", "anc")));

        addProduct(new Product(4, "Apple MacBook Air M3", "Laptops", 114900.00, 4.9,
                "Thin and fast laptop with Apple M3 chip and Liquid Retina display",
                Arrays.asList("apple", "macbook", "laptop", "m3", "macos", "lightweight")));

        addProduct(new Product(5, "Dell XPS 15 OLED", "Laptops", 169990.00, 4.5,
                "High performance creator laptop with 4K OLED InfinityEdge display",
                Arrays.asList("dell", "xps", "laptop", "windows", "oled", "intel")));

        addProduct(new Product(6, "Nike Air Zoom Pegasus 40", "Footwear", 10495.00, 4.4,
                "Responsive daily running shoes with dual Zoom Air units",
                Arrays.asList("nike", "shoes", "running", "sneakers", "sports", "footwear")));

        addProduct(new Product(7, "Adidas Ultraboost Light", "Footwear", 14999.00, 4.5,
                "Ultimate energy return cushioning lightweight running sneakers",
                Arrays.asList("adidas", "ultraboost", "sneakers", "running", "footwear")));

        addProduct(new Product(8, "Logitech MX Master 3S", "Accessories", 8995.00, 4.8,
                "Performance wireless ergonomic mouse with ultra-fast scrolling",
                Arrays.asList("logitech", "mouse", "ergonomic", "wireless", "bluetooth", "office")));

        addProduct(new Product(9, "Apple Watch Ultra 2", "Wearables", 89900.00, 4.7,
                "Rugged smartwatch with precision dual-frequency GPS and titanium case",
                Arrays.asList("apple", "watch", "smartwatch", "fitness", "gps", "rugged")));

        addProduct(new Product(10, "Bose QuietComfort Ultra Earbuds", "Audio", 25900.00, 4.5,
                "Spatial audio noise cancelling wireless earbuds with high fidelity sound",
                Arrays.asList("bose", "earbuds", "audio", "noise cancelling", "bluetooth")));

        addProduct(new Product(11, "Kindle Paperwhite Signature", "Electronics", 17999.00, 4.7,
                "6.8-inch display e-reader with wireless charging and auto-adjusting light",
                Arrays.asList("amazon", "kindle", "ebook", "reader", "paperwhite")));

        addProduct(new Product(12, "Canon EOS R6 Mark II", "Cameras", 215995.00, 4.9,
                "Full-frame mirrorless camera with 4K 60p video and continuous shooting",
                Arrays.asList("canon", "camera", "mirrorless", "photography", "video", "4k")));

        // Global Flagship Smartphones
        addProduct(new Product(18, "Google Pixel 9 Pro XL", "Smartphones", 124999.00, 4.7,
                "Google Tensor G4 flagship with Gemini AI, 50MP triple camera system and Super Actua display",
                Arrays.asList("google", "pixel", "pixel 9", "pixel 9 pro", "pixel 9 pro xl", "android", "gemini", "phone", "5g")));

        addProduct(new Product(19, "OnePlus 12 5G", "Smartphones", 64999.00, 4.6,
                "Snapdragon 8 Gen 3 flagship with 4th Gen Hasselblad Camera and 100W SUPERVOOC charging",
                Arrays.asList("oneplus", "oneplus 12", "hasselblad", "snapdragon", "android", "phone", "5g")));

        addProduct(new Product(20, "Samsung Galaxy Z Fold 6", "Smartphones", 164999.00, 4.6,
                "Next-gen foldable smartphone with Galaxy AI, Armor Aluminum frame and dual dynamic AMOLED displays",
                Arrays.asList("samsung", "galaxy", "z fold", "z fold 6", "foldable", "android", "phone", "5g")));

        addProduct(new Product(21, "Xiaomi 14 Ultra", "Smartphones", 99999.00, 4.7,
                "Leica quad optical lens system with 1-inch sensor, Snapdragon 8 Gen 3 and titanium edition",
                Arrays.asList("xiaomi", "xiaomi 14", "xiaomi 14 ultra", "leica", "android", "phone", "5g")));

        // Global Premium Laptops & Computing
        addProduct(new Product(22, "Apple MacBook Pro 16 M3 Max", "Laptops", 349900.00, 4.9,
                "Pro workstation laptop with 16-core CPU, 40-core GPU, Liquid Retina XDR and up to 128GB unified memory",
                Arrays.asList("apple", "macbook", "macbook pro", "m3 max", "laptop", "macos", "workstation")));

        addProduct(new Product(23, "Lenovo ThinkPad X1 Carbon Gen 12", "Laptops", 189990.00, 4.7,
                "Ultra-lightweight enterprise carbon fiber laptop with Intel Core Ultra 7 and 2.8K OLED display",
                Arrays.asList("lenovo", "thinkpad", "x1 carbon", "laptop", "intel", "windows", "business")));

        addProduct(new Product(24, "ASUS ROG Zephyrus G16", "Laptops", 229990.00, 4.8,
                "Ultra-slim gaming laptop with Intel Core Ultra 9, NVIDIA GeForce RTX 4080 and 240Hz OLED Nebula display",
                Arrays.asList("asus", "rog", "zephyrus", "gaming", "laptop", "rtx 4080", "nvidia", "windows")));

        // Global Audio & Headphones
        addProduct(new Product(25, "Apple AirPods Pro 2", "Audio", 24900.00, 4.8,
                "True wireless active noise cancelling earbuds with H2 chip, Adaptive Audio and USB-C MagSafe case",
                Arrays.asList("apple", "airpods", "airpods pro", "airpods pro 2", "audio", "earbuds", "bluetooth", "anc")));

        addProduct(new Product(26, "Apple AirPods Max", "Audio", 59900.00, 4.6,
                "High-fidelity over-ear spatial audio headphones with active noise cancellation and transparency mode",
                Arrays.asList("apple", "airpods max", "headphones", "audio", "anc", "spatial audio", "bluetooth")));

        addProduct(new Product(27, "Sennheiser Momentum 4 Wireless", "Audio", 29990.00, 4.7,
                "Audiophile wireless over-ear headphones with 60-hour battery life and customizable sound EQ",
                Arrays.asList("sennheiser", "momentum", "momentum 4", "headphones", "audio", "wireless", "anc")));

        // Global Gaming Consoles & Gear
        addProduct(new Product(28, "Sony PlayStation 5 Slim", "Gaming", 54990.00, 4.9,
                "Next-generation gaming console with ultra-high speed 1TB SSD, ray tracing and DualSense haptic feedback",
                Arrays.asList("sony", "playstation", "ps5", "ps5 slim", "console", "gaming", "dualsense")));

        addProduct(new Product(29, "Microsoft Xbox Series X", "Gaming", 55990.00, 4.8,
                "Fastest most powerful Xbox console with 12 teraflops processing power and 4K 120FPS gaming",
                Arrays.asList("microsoft", "xbox", "series x", "xbox series x", "console", "gaming", "4k")));

        addProduct(new Product(30, "Nintendo Switch OLED Model", "Gaming", 31999.00, 4.8,
                "Handheld hybrid gaming console with 7-inch vibrant OLED screen, wide adjustable stand and 64GB storage",
                Arrays.asList("nintendo", "switch", "switch oled", "gaming", "handheld", "console")));

        addProduct(new Product(31, "Valve Steam Deck OLED 512GB", "Gaming", 59999.00, 4.8,
                "Portable PC gaming handheld with 90Hz HDR OLED display and custom AMD Zen 2 APU",
                Arrays.asList("valve", "steam deck", "steam", "gaming", "handheld", "pc gaming")));

        // Global Smartwatches & Wearables
        addProduct(new Product(32, "Samsung Galaxy Watch Ultra", "Wearables", 59999.00, 4.6,
                "Rugged titanium smartwatch with multi-sport bio-active sensors, dual-frequency GPS and 100hr battery life",
                Arrays.asList("samsung", "galaxy watch", "watch ultra", "smartwatch", "wearable", "fitness", "android")));

        addProduct(new Product(33, "Garmin Fenix 7 Pro Sapphire Solar", "Wearables", 94990.00, 4.8,
                "Multisport GPS multisport watch with solar charging sapphire lens, built-in LED flashlight and TopoActive maps",
                Arrays.asList("garmin", "fenix", "fenix 7", "smartwatch", "gps", "solar", "fitness", "wearable")));

        // Global Cameras & Photography
        addProduct(new Product(34, "Sony Alpha 7 IV Mirrorless Camera", "Cameras", 224990.00, 4.9,
                "Full-frame hybrid mirrorless camera with 33MP Exmor R CMOS sensor, 4K 60p and real-time eye AF",
                Arrays.asList("sony", "a7 iv", "alpha 7", "camera", "mirrorless", "full frame", "photography", "4k")));

        addProduct(new Product(35, "GoPro HERO 13 Black", "Cameras", 44990.00, 4.7,
                "Rugged waterproof 5.3K60 action camera with HyperSmooth 6.0 stabilization and Enduro battery",
                Arrays.asList("gopro", "hero 13", "hero", "action camera", "waterproof", "video", "4k", "5k")));

        addProduct(new Product(36, "DJI Mini 4 Pro Drone", "Cameras", 94990.00, 4.8,
                "Ultralight sub-249g folding drone with omnidirectional obstacle sensing, 4K/60fps HDR video and 20km transmission",
                Arrays.asList("dji", "mini 4 pro", "drone", "camera", "aerial", "4k", "video")));

        // Global Footwear & Sportswear
        addProduct(new Product(37, "Nike Air Jordan 1 Retro High OG", "Footwear", 16995.00, 4.9,
                "Iconic heritage basketball sneakers with premium genuine leather upper and encapsulated Air-Sole unit",
                Arrays.asList("nike", "jordan", "air jordan", "jordan 1", "sneakers", "shoes", "footwear", "basketball")));

        addProduct(new Product(38, "New Balance 990v6 Made in USA", "Footwear", 23999.00, 4.7,
                "Premium lifestyle running sneakers with FuelCell foam midsole cushioning and pigskin suede upper",
                Arrays.asList("new balance", "nb", "990v6", "990", "sneakers", "running", "shoes", "footwear")));

        // Global Smart Home & Appliances
        addProduct(new Product(39, "Dyson V15 Detect Cordless Vacuum", "Home", 65900.00, 4.8,
                "Laser slim fluffy intelligent vacuum cleaner with piezo acoustic dust sensor and 240AW suction power",
                Arrays.asList("dyson", "v15", "vacuum", "cordless", "home", "appliances", "cleaning")));

        addProduct(new Product(40, "Sony BRAVIA XR 65-inch OLED 4K TV", "Electronics", 239990.00, 4.9,
                "Cognitive Processor XR OLED TV with Acoustic Surface Audio+, Dolby Vision Atmos and 120Hz HDMI 2.1",
                Arrays.asList("sony", "bravia", "oled", "tv", "4k", "television", "smart tv", "dolby vision")));
    }

    /**
     * Registers a product into catalog and indexes tokens in Trie.
     */
    public void addProduct(Product product) {
        catalog.add(product);

        // Index all normalized words and prefixes in the Trie
        List<String> tokens = TextNormalizer.tokenize(product.getCombinedSearchText());
        for (String token : tokens) {
            prefixTrie.insert(token, product.getId());
        }
    }

    public List<Product> getAllProducts() {
        return Collections.unmodifiableList(catalog);
    }

    /**
     * Returns autocomplete query suggestions based on Trie prefix matching.
     *
     * @param query Raw prefix query
     * @param limit Maximum suggestions count
     * @return List of matching keyword suggestions
     */
    public List<String> getSuggestions(String query, int limit) {
        String normalized = TextNormalizer.normalize(query);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        // If multiple words, get suggestion for the last typing token
        List<String> tokens = TextNormalizer.tokenize(normalized);
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }
        String lastToken = tokens.get(tokens.size() - 1);
        return prefixTrie.getSuggestions(lastToken, limit);
    }

    /**
     * Executes the combined multi-DSA search and returns ranked results.
     *
     * @param rawQuery User search query
     * @return Ranked list of search results
     */
    public List<SearchResult> search(String rawQuery) {
        List<SearchResult> results = new ArrayList<>();
        String normalizedQuery = TextNormalizer.normalize(rawQuery);
        if (normalizedQuery.isEmpty()) {
            // Empty query: return all products sorted by rating
            for (Product p : catalog) {
                results.add(new SearchResult(p, 1.0, 1.0, 1.0, 1.0, 1.0, false, "All"));
            }
            Collections.sort(results);
            return results;
        }

        List<String> queryTokens = TextNormalizer.tokenize(normalizedQuery);
        Set<Integer> prefixCandidateIds = new HashSet<>();
        for (String qToken : queryTokens) {
            prefixCandidateIds.addAll(prefixTrie.getProductIdsWithPrefix(qToken));
        }

        for (Product product : catalog) {
            String normName = TextNormalizer.normalize(product.getName());
            String normCategory = TextNormalizer.normalize(product.getCategory());
            String normCombined = TextNormalizer.normalize(product.getCombinedSearchText());
            List<String> productTokens = TextNormalizer.tokenize(normCombined);

            // 1. Trie Score: presence in prefix query tree
            double trieScore = prefixCandidateIds.contains(product.getId()) ? 1.0 : 0.0;

            // 2. Rabin-Karp Substring Score
            // Check exact substring match on product name or combined text
            double rkNameScore = RabinKarp.computeMatchScore(normName, normalizedQuery);
            double rkCombinedScore = RabinKarp.computeMatchScore(normCombined, normalizedQuery);
            double rabinKarpScore = Math.max(rkNameScore, rkCombinedScore);

            // 3. Levenshtein DP Fuzzy Score
            // Compare overall query with name, as well as token-level best matches
            double levWholeSim = LevenshteinDP.computeSimilarity(normalizedQuery, normName);
            double levTokenSum = 0.0;
            for (String qTok : queryTokens) {
                levTokenSum += LevenshteinDP.bestWordSimilarity(qTok, normCombined);
            }
            double levTokenAvg = queryTokens.isEmpty() ? 0.0 : (levTokenSum / queryTokens.size());
            double levenshteinScore = Math.max(levWholeSim, levTokenAvg);

            // 4. Max Flow Bipartite Matching Score
            double maxFlowScore = MaxFlow.computeBipartiteMatchScore(queryTokens, productTokens);

            // Determine if match is predominantly fuzzy (typo-tolerant)
            boolean isFuzzy = (rabinKarpScore < 0.6) && (levenshteinScore >= 0.65 || maxFlowScore >= 0.5);

            // Match Type label
            String matchType;
            if (rabinKarpScore >= 0.95 || normName.equals(normalizedQuery)) {
                matchType = "Exact";
            } else if (rabinKarpScore > 0.0) {
                matchType = "Substring";
            } else if (trieScore > 0.0 && levenshteinScore >= 0.8) {
                matchType = "Prefix";
            } else if (isFuzzy) {
                matchType = "Fuzzy";
            } else if (maxFlowScore > 0.0) {
                matchType = "Attribute Match";
            } else {
                matchType = "Low Match";
            }

            // Weighted composite ranking formula:
            // High weight on exact/substring (Rabin-Karp), fuzzy tolerance (Levenshtein),
            // flow optimization (MaxFlow) and prefix presence (Trie).
            double totalScore = (0.35 * rabinKarpScore)
                              + (0.30 * levenshteinScore)
                              + (0.25 * maxFlowScore)
                              + (0.10 * trieScore);

            // Rating small boost: +0.02 * (rating / 5.0)
            totalScore += 0.02 * (product.getRating() / 5.0);

            // Filter out non-matching products (threshold: 0.20)
            if (totalScore >= 0.20 || rabinKarpScore > 0.0 || maxFlowScore >= 0.40) {
                results.add(new SearchResult(
                        product,
                        round(totalScore, 3),
                        round(trieScore, 3),
                        round(rabinKarpScore, 3),
                        round(levenshteinScore, 3),
                        round(maxFlowScore, 3),
                        isFuzzy,
                        matchType
                ));
            }
        }

        Collections.sort(results);
        return results;
    }

    /**
     * Executes the DSA matching and ranking pipeline on a custom list of products
     * (e.g. products fetched live from online web services).
     */
    public List<SearchResult> searchProductList(List<Product> products, String rawQuery) {
        List<SearchResult> results = new ArrayList<>();
        String normalizedQuery = TextNormalizer.normalize(rawQuery);

        if (normalizedQuery.isEmpty()) {
            for (Product p : products) {
                results.add(new SearchResult(p, 1.0, 1.0, 1.0, 1.0, 1.0, false, "Live Web Product"));
            }
            Collections.sort(results);
            return results;
        }

        // Build temporary Trie for online products
        Trie tempTrie = new Trie();
        for (Product p : products) {
            List<String> tokens = TextNormalizer.tokenize(p.getCombinedSearchText());
            for (String token : tokens) {
                tempTrie.insert(token, p.getId());
            }
        }

        List<String> queryTokens = TextNormalizer.tokenize(normalizedQuery);
        Set<Integer> prefixCandidateIds = new HashSet<>();
        for (String qToken : queryTokens) {
            prefixCandidateIds.addAll(tempTrie.getProductIdsWithPrefix(qToken));
        }

        for (Product product : products) {
            String normName = TextNormalizer.normalize(product.getName());
            String normCombined = TextNormalizer.normalize(product.getCombinedSearchText());
            List<String> productTokens = TextNormalizer.tokenize(normCombined);

            // 1. Trie Score
            double trieScore = prefixCandidateIds.contains(product.getId()) ? 1.0 : 0.0;

            // 2. Rabin-Karp Substring Score
            double rkNameScore = RabinKarp.computeMatchScore(normName, normalizedQuery);
            double rkCombinedScore = RabinKarp.computeMatchScore(normCombined, normalizedQuery);
            double rabinKarpScore = Math.max(rkNameScore, rkCombinedScore);

            // 3. Levenshtein DP Fuzzy Score
            double levWholeSim = LevenshteinDP.computeSimilarity(normalizedQuery, normName);
            double levTokenSum = 0.0;
            for (String qTok : queryTokens) {
                levTokenSum += LevenshteinDP.bestWordSimilarity(qTok, normCombined);
            }
            double levTokenAvg = queryTokens.isEmpty() ? 0.0 : (levTokenSum / queryTokens.size());
            double levenshteinScore = Math.max(levWholeSim, levTokenAvg);

            // 4. Max Flow Bipartite Matching Score
            double maxFlowScore = MaxFlow.computeBipartiteMatchScore(queryTokens, productTokens);

            boolean isFuzzy = (rabinKarpScore < 0.6) && (levenshteinScore >= 0.60 || maxFlowScore >= 0.5);

            String matchType;
            if (rabinKarpScore >= 0.95 || normName.equals(normalizedQuery)) {
                matchType = "Exact";
            } else if (rabinKarpScore > 0.0) {
                matchType = "Substring";
            } else if (trieScore > 0.0 && levenshteinScore >= 0.8) {
                matchType = "Prefix";
            } else if (isFuzzy) {
                matchType = "Fuzzy";
            } else if (maxFlowScore > 0.0) {
                matchType = "Attribute Match";
            } else {
                matchType = "Online Match";
            }

            double totalScore = (0.35 * rabinKarpScore)
                              + (0.30 * levenshteinScore)
                              + (0.25 * maxFlowScore)
                              + (0.10 * trieScore);

            totalScore += 0.02 * (product.getRating() / 5.0);

            // Include if relevant or if query was matched
            results.add(new SearchResult(
                    product,
                    round(totalScore, 3),
                    round(trieScore, 3),
                    round(rabinKarpScore, 3),
                    round(levenshteinScore, 3),
                    round(maxFlowScore, 3),
                    isFuzzy,
                    matchType
            ));
        }

        Collections.sort(results);
        return results;
    }

    private static double round(double val, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(val * factor) / factor;
    }
}
