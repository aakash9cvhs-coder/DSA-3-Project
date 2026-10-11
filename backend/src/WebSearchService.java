import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * WebSearchService provides a lightweight, zero-dependency embedded HTTP server
 * using standard Java standard library (com.sun.net.httpserver).
 *
 * Exposes REST API endpoints:
 * - GET /api/search?q=iphone : executes matching engine and returns JSON results
 * - GET /api/online-search?q=phone : queries live online product catalog & scores via DSA engine
 * - GET /api/suggest?q=iph   : returns Trie autocomplete suggestions
 * - GET /api/products        : returns full catalog
 * - Serves frontend static files (index.html, style.css, script.js)
 */
public class WebSearchService {

    private final MatchingEngine matchingEngine;
    private final int port;
    private HttpServer server;

    public WebSearchService(MatchingEngine matchingEngine, int port) {
        this.matchingEngine = matchingEngine;
        this.port = port;
    }

    /**
     * Starts the embedded HTTP server.
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // API Contexts
        server.createContext("/api/search", new SearchHandler(matchingEngine));
        server.createContext("/api/online-search", new OnlineSearchHandler(matchingEngine));
        server.createContext("/api/suggest", new SuggestHandler(matchingEngine));
        server.createContext("/api/products", new ProductsHandler(matchingEngine));
        server.createContext("/api/dashboard", new DashboardHandler(matchingEngine));
        server.createContext("/api/auth/signup", new AuthSignUpHandler());
        server.createContext("/api/auth/login", new AuthLoginHandler());
        server.createContext("/api/auth/current", new AuthCurrentHandler());

        // Static Frontend file handler
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // multi-threaded pool
        server.start();
        System.out.println("==================================================");
        System.out.println("  DSA-3 E-Commerce Entity Matcher Service Started!");
        System.out.println("  Web Interface : http://localhost:" + port + "/");
        System.out.println("  Search API    : http://localhost:" + port + "/api/search?q=iphone");
        System.out.println("  Suggest API   : http://localhost:" + port + "/api/suggest?q=app");
        System.out.println("  Auth API      : http://localhost:" + port + "/api/auth/login");
        System.out.println("==================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * Handler for /api/search?q=...
     */
    static class SearchHandler implements HttpHandler {
        private final MatchingEngine engine;

        public SearchHandler(MatchingEngine engine) {
            this.engine = engine;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String query = params.getOrDefault("q", "");

            List<MatchingEngine.SearchResult> results = engine.search(query);
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"query\": \"").append(escapeJson(query)).append("\",\n");
            json.append("  \"count\": ").append(results.size()).append(",\n");
            json.append("  \"results\": [\n");

            for (int i = 0; i < results.size(); i++) {
                MatchingEngine.SearchResult r = results.get(i);
                MatchingEngine.Product p = r.getProduct();

                json.append("    {\n");
                json.append("      \"id\": ").append(p.getId()).append(",\n");
                json.append("      \"name\": \"").append(escapeJson(p.getName())).append("\",\n");
                json.append("      \"category\": \"").append(escapeJson(p.getCategory())).append("\",\n");
                json.append("      \"price\": ").append(p.getPrice()).append(",\n");
                json.append("      \"rating\": ").append(p.getRating()).append(",\n");
                json.append("      \"description\": \"").append(escapeJson(p.getDescription())).append("\",\n");
                json.append("      \"score\": ").append(r.getTotalScore()).append(",\n");
                json.append("      \"matchType\": \"").append(escapeJson(r.getMatchType())).append("\",\n");
                json.append("      \"isFuzzy\": ").append(r.isFuzzy()).append(",\n");
                json.append("      \"scores\": {\n");
                json.append("        \"trie\": ").append(r.getTrieScore()).append(",\n");
                json.append("        \"rabinKarp\": ").append(r.getRabinKarpScore()).append(",\n");
                json.append("        \"levenshtein\": ").append(r.getLevenshteinScore()).append(",\n");
                json.append("        \"maxFlow\": ").append(r.getMaxFlowScore()).append("\n");
                json.append("      }\n");
                json.append("    }");
                if (i < results.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("  ]\n");
            json.append("}");

            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    /**
     * Handler for /api/online-search?q=...
     * Queries live web product API (SerpApi / DummyJSON) server-side,
     * converts to Indian Rupees (₹ INR), runs the core DSA matching engine
     * (Trie, Rabin-Karp, Levenshtein DP, Max-Flow), and returns ranked results.
     */
    static class OnlineSearchHandler implements HttpHandler {
        private final MatchingEngine engine;
        private static final double USD_TO_INR = 83.5;
        private static final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(2500))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        public OnlineSearchHandler(MatchingEngine engine) {
            this.engine = engine;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String query = params.getOrDefault("q", "").trim();
            String apiKey = params.getOrDefault("apiKey", "").trim();
            if (apiKey.isEmpty()) {
                apiKey = params.getOrDefault("api_key", "").trim();
            }

            // Only query and return live online web candidates (no saved local products)
            List<MatchingEngine.Product> onlineProducts = new ArrayList<>();

            String searchSource = "online-web";

            // If an API key is provided, query SerpApi Google Shopping engine
            if (!apiKey.isEmpty() && !query.isEmpty()) {
                try {
                    String serpApiUrl = "https://serpapi.com/search.json?engine=google_shopping&q="
                            + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                            + "&google_domain=google.co.in&gl=in&hl=en&num=20&api_key="
                            + java.net.URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name());

                    HttpRequest serpReq = HttpRequest.newBuilder()
                            .uri(URI.create(serpApiUrl))
                            .timeout(Duration.ofMillis(5000))
                            .header("Accept", "application/json")
                            .GET()
                            .build();

                    HttpResponse<String> serpRes = httpClient.send(serpReq, HttpResponse.BodyHandlers.ofString());
                    if (serpRes.statusCode() == 200) {
                        List<MatchingEngine.Product> serpProds = parseSerpApiShoppingProducts(serpRes.body());
                        if (!serpProds.isEmpty()) {
                            searchSource = "serpapi-live-web";
                            for (MatchingEngine.Product sp : serpProds) {
                                boolean exists = false;
                                for (MatchingEngine.Product existing : onlineProducts) {
                                    if (existing.getId() == sp.getId()) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    onlineProducts.add(sp);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Fall back to DummyJSON below
                }
            }

            // Fallback/enrich with online web catalog
            if (!"serpapi-live-web".equals(searchSource)) {
                try {
                    String onlineApiUrl = query.isEmpty()
                            ? "https://dummyjson.com/products?limit=30"
                            : "https://dummyjson.com/products/search?q=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8.name()) + "&limit=30";

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(onlineApiUrl))
                            .timeout(Duration.ofMillis(2500))
                            .header("Accept", "application/json")
                            .GET()
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        searchSource = "dummyjson-web-catalog";
                        List<MatchingEngine.Product> webProds = parseDummyJsonProducts(response.body());
                        for (MatchingEngine.Product wp : webProds) {
                            boolean exists = false;
                            for (MatchingEngine.Product existing : onlineProducts) {
                                if (existing.getId() == wp.getId()) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                onlineProducts.add(wp);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Network timeout or offline - seamlessly proceed with candidates
                }
            }

            // Run DSA Engine (Trie, Rabin-Karp, Levenshtein DP, Max Flow) on all candidates
            List<MatchingEngine.SearchResult> searchResults = engine.searchProductList(onlineProducts, query);

            // Filter out low-matching irrelevant items when user entered a specific query
            if (!query.isEmpty()) {
                List<MatchingEngine.SearchResult> filteredResults = new ArrayList<>();
                for (MatchingEngine.SearchResult sr : searchResults) {
                    if (sr.getTotalScore() >= 0.22 || sr.getRabinKarpScore() > 0.0 || sr.getLevenshteinScore() >= 0.50 || sr.getMaxFlowScore() >= 0.40) {
                        filteredResults.add(sr);
                    }
                }
                searchResults = filteredResults;
            }

            // Serialize results in standardized JSON format
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"query\": \"").append(escapeJson(query)).append("\",\n");
            json.append("  \"source\": \"").append(escapeJson(searchSource)).append("\",\n");
            json.append("  \"hasApiKey\": ").append(!apiKey.isEmpty()).append(",\n");
            json.append("  \"count\": ").append(searchResults.size()).append(",\n");
            json.append("  \"results\": [\n");

            for (int i = 0; i < searchResults.size(); i++) {
                MatchingEngine.SearchResult r = searchResults.get(i);
                MatchingEngine.Product p = r.getProduct();

                json.append("    {\n");
                json.append("      \"id\": ").append(p.getId()).append(",\n");
                json.append("      \"product\": {\n");
                json.append("        \"id\": ").append(p.getId()).append(",\n");
                json.append("        \"name\": \"").append(escapeJson(p.getName())).append("\",\n");
                json.append("        \"category\": \"").append(escapeJson(p.getCategory())).append("\",\n");
                json.append("        \"price\": ").append(Math.round(p.getPrice())).append(",\n");
                json.append("        \"rating\": ").append(p.getRating()).append(",\n");
                json.append("        \"description\": \"").append(escapeJson(p.getDescription())).append("\",\n");
                json.append("        \"thumbnail\": \"").append(escapeJson(p.getThumbnail())).append("\"\n");
                json.append("      },\n");
                json.append("      \"score\": ").append(r.getTotalScore()).append(",\n");
                json.append("      \"matchType\": \"").append(escapeJson(r.getMatchType())).append("\",\n");
                json.append("      \"isFuzzy\": ").append(r.isFuzzy()).append(",\n");
                json.append("      \"scores\": {\n");
                json.append("        \"trie\": ").append(r.getTrieScore()).append(",\n");
                json.append("        \"rabinKarp\": ").append(r.getRabinKarpScore()).append(",\n");
                json.append("        \"levenshtein\": ").append(r.getLevenshteinScore()).append(",\n");
                json.append("        \"maxFlow\": ").append(r.getMaxFlowScore()).append("\n");
                json.append("      }\n");
                json.append("    }");
                if (i < searchResults.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ]\n");
            json.append("}");

            sendJsonResponse(exchange, 200, json.toString());
        }

        /**
         * Parses SerpApi Google Shopping items ("shopping_results" array).
         */
        private List<MatchingEngine.Product> parseSerpApiShoppingProducts(String body) {
            List<MatchingEngine.Product> list = new ArrayList<>();
            if (body == null || body.isEmpty()) return list;

            int shopIdx = body.indexOf("\"shopping_results\":[");
            if (shopIdx == -1) shopIdx = body.indexOf("\"shopping_results\": [");
            if (shopIdx == -1) return list;

            int start = body.indexOf('[', shopIdx);
            int end = body.indexOf(']', start);
            if (start == -1 || end == -1 || end <= start) return list;

            String arrayContent = body.substring(start + 1, end).trim();
            int objStart = -1;
            int braceDepth = 0;
            int counter = 5000;

            for (int i = 0; i < arrayContent.length(); i++) {
                char c = arrayContent.charAt(i);
                if (c == '{') {
                    if (braceDepth == 0) objStart = i;
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0 && objStart != -1) {
                        String objJson = arrayContent.substring(objStart, i + 1);
                        String title = extractString(objJson, "\"title\":");
                        String source = extractString(objJson, "\"source\":");
                        String snippet = extractString(objJson, "\"snippet\":");
                        String thumb = extractString(objJson, "\"thumbnail\":");
                        double extractedPrice = extractPriceFromString(extractString(objJson, "\"price\":"));
                        if (extractedPrice <= 0) {
                            extractedPrice = extractDouble(objJson, "\"extracted_price\":");
                        }
                        if (extractedPrice <= 0) {
                            extractedPrice = 1499.0;
                        }
                        double rating = extractDouble(objJson, "\"rating\":");
                        if (rating <= 0.0) rating = 4.5;

                        if (!title.isEmpty()) {
                            String category = source.isEmpty() ? "Web Store" : source;
                            String desc = snippet.isEmpty() ? ("Live Web Result from " + category) : snippet;
                            List<String> tags = new ArrayList<>(TextNormalizer.tokenize(title + " " + category));
                            list.add(new MatchingEngine.Product(++counter, title, category, Math.round(extractedPrice), rating, desc, tags, thumb));
                        }
                        objStart = -1;
                    }
                }
            }
            return list;
        }

        private double extractPriceFromString(String priceStr) {
            if (priceStr == null || priceStr.isEmpty()) return 0.0;
            StringBuilder num = new StringBuilder();
            boolean hasDot = false;
            for (char c : priceStr.toCharArray()) {
                if (Character.isDigit(c)) {
                    num.append(c);
                } else if (c == '.' && !hasDot) {
                    num.append(c);
                    hasDot = true;
                }
            }
            try {
                return num.length() > 0 ? Double.parseDouble(num.toString()) : 0.0;
            } catch (Exception e) {
                return 0.0;
            }
        }

        /**
         * Parses DummyJSON products array with zero external dependencies.
         */
        private List<MatchingEngine.Product> parseDummyJsonProducts(String body) {
            List<MatchingEngine.Product> list = new ArrayList<>();
            if (body == null || body.isEmpty()) return list;

            int prodIdx = body.indexOf("\"products\":[");
            if (prodIdx == -1) prodIdx = body.indexOf("\"products\": [");
            if (prodIdx == -1) return list;

            int start = body.indexOf('[', prodIdx);
            int end = body.lastIndexOf(']');
            if (start == -1 || end == -1 || end <= start) return list;

            String arrayContent = body.substring(start + 1, end).trim();
            int objStart = -1;
            int braceDepth = 0;

            for (int i = 0; i < arrayContent.length(); i++) {
                char c = arrayContent.charAt(i);
                if (c == '{') {
                    if (braceDepth == 0) objStart = i;
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0 && objStart != -1) {
                        String objJson = arrayContent.substring(objStart, i + 1);
                        MatchingEngine.Product p = parseSingleProduct(objJson);
                        if (p != null) {
                            list.add(p);
                        }
                        objStart = -1;
                    }
                }
            }
            return list;
        }

        private MatchingEngine.Product parseSingleProduct(String json) {
            int id = extractInt(json, "\"id\":");
            String title = extractString(json, "\"title\":");
            String desc = extractString(json, "\"description\":");
            String cat = extractString(json, "\"category\":");
            double usdPrice = extractDouble(json, "\"price\":");
            double rating = extractDouble(json, "\"rating\":");
            String thumb = extractString(json, "\"thumbnail\":");

            if (title.isEmpty()) return null;
            if (rating <= 0.0) rating = 4.5;
            double inrPrice = Math.round(usdPrice * USD_TO_INR);
            if (inrPrice <= 0) inrPrice = 999.0;

            List<String> tags = new ArrayList<>(TextNormalizer.tokenize(title + " " + cat));
            return new MatchingEngine.Product(id, title, cat, inrPrice, rating, desc, tags, thumb);
        }

        private String extractString(String json, String key) {
            int idx = json.indexOf(key);
            if (idx == -1) return "";
            int startQuote = json.indexOf('"', idx + key.length());
            if (startQuote == -1) return "";
            StringBuilder sb = new StringBuilder();
            boolean esc = false;
            for (int i = startQuote + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (esc) {
                    if (c == 'n') sb.append(' ');
                    else sb.append(c);
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private double extractDouble(String json, String key) {
            int idx = json.indexOf(key);
            if (idx == -1) return 0.0;
            int start = idx + key.length();
            while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) end++;
            try {
                return Double.parseDouble(json.substring(start, end));
            } catch (Exception e) {
                return 0.0;
            }
        }

        private int extractInt(String json, String key) {
            return (int) extractDouble(json, key);
        }
    }

    /**
     * Handler for /api/suggest?q=...
     */
    static class SuggestHandler implements HttpHandler {
        private final MatchingEngine engine;

        public SuggestHandler(MatchingEngine engine) {
            this.engine = engine;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String query = params.getOrDefault("q", "");
            List<String> suggestions = engine.getSuggestions(query, 6);

            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"query\": \"").append(escapeJson(query)).append("\",\n");
            json.append("  \"suggestions\": [");
            for (int i = 0; i < suggestions.size(); i++) {
                json.append("\"").append(escapeJson(suggestions.get(i))).append("\"");
                if (i < suggestions.size() - 1) {
                    json.append(", ");
                }
            }
            json.append("]\n");
            json.append("}");

            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    /**
     * Handler for /api/products
     */
    static class ProductsHandler implements HttpHandler {
        private final MatchingEngine engine;

        public ProductsHandler(MatchingEngine engine) {
            this.engine = engine;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            List<MatchingEngine.Product> products = engine.getAllProducts();
            StringBuilder json = new StringBuilder();
            json.append("[\n");
            for (int i = 0; i < products.size(); i++) {
                MatchingEngine.Product p = products.get(i);
                json.append("  {\n");
                json.append("    \"id\": ").append(p.getId()).append(",\n");
                json.append("    \"name\": \"").append(escapeJson(p.getName())).append("\",\n");
                json.append("    \"category\": \"").append(escapeJson(p.getCategory())).append("\",\n");
                json.append("    \"price\": ").append(p.getPrice()).append(",\n");
                json.append("    \"rating\": ").append(p.getRating()).append(",\n");
                json.append("    \"description\": \"").append(escapeJson(p.getDescription())).append("\"\n");
                json.append("  }");
                if (i < products.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("]");

            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    /**
     * Handler for /api/dashboard
     * Returns real statistics computed from the Core Java MatchingEngine catalog & seller listings:
     * - Total Products
     * - Total Listings
     * - Matched Listings
     * - Unmatched Listings
     * - High vs Low Confidence breakdowns
     * - Recent matches table with seller listing, canonical product, similarity, status
     */
    static class DashboardHandler implements HttpHandler {
        private final MatchingEngine engine;

        public DashboardHandler(MatchingEngine engine) {
            this.engine = engine;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            List<MatchingEngine.Product> products = engine.getAllProducts();
            int totalProducts = products.size();

            // Realistic seller listings from Amazon India, Flipkart, Croma, Reliance Digital, Vijay Sales
            // Stored in Core Java ArrayList and matched using the real MatchingEngine pipeline
            List<String[]> sellerListings = new ArrayList<>();
            sellerListings.add(new String[]{"Apple iPhone 15 Pro (128 GB) - Natural Titanium", "Amazon India"});
            sellerListings.add(new String[]{"SAMSUNG Galaxy S24 Ultra 5G (Titanium Gray, 256 GB)", "Flipkart"});
            sellerListings.add(new String[]{"Sony WH-1000XM5 Over-Ear ANC Headphones (Silver)", "Croma"});
            sellerListings.add(new String[]{"Apple MacBook Air 13.6-inch M3 Chip 8GB/256GB SSD", "Reliance Digital"});
            sellerListings.add(new String[]{"Dell XPS 15 9530 Core i7-13700H 16GB 1TB OLED", "Vijay Sales"});
            sellerListings.add(new String[]{"Nike Air Zoom Pegasus 40 Men Road Running Shoes Size 9", "Amazon India"});
            sellerListings.add(new String[]{"Adidas Ultraboost Light Running Shoes Core Black", "Flipkart"});
            sellerListings.add(new String[]{"Logitech MX Master 3S Bluetooth Performance Mouse Graphite", "Croma"});
            sellerListings.add(new String[]{"Apple Watch Ultra 2 GPS + Cellular 49mm Titanium", "Amazon India"});
            sellerListings.add(new String[]{"Bose QC Ultra Wireless Earbuds with Spatial Audio", "Reliance Digital"});
            sellerListings.add(new String[]{"Amazon Kindle Paperwhite Signature Edition 32GB", "Amazon India"});
            sellerListings.add(new String[]{"Canon EOS R6 Mark II Mirrorless Camera Body Only", "Vijay Sales"});
            sellerListings.add(new String[]{"Generic Protective Silicon Case for Smartphone 6.1-inch", "Flipkart"});
            sellerListings.add(new String[]{"Universal USB-C Fast Charging Cable Braided 1.5m", "Croma"});
            sellerListings.add(new String[]{"RGB Mechanical Gaming Keyboard Blue Switches 87 Keys", "Amazon India"});

            int totalListings = sellerListings.size();
            int matchedCount = 0;
            int highConfidenceCount = 0;
            int lowConfidenceCount = 0;

            StringBuilder recentMatchesJson = new StringBuilder();
            recentMatchesJson.append("[\n");

            for (int i = 0; i < sellerListings.size(); i++) {
                String listingTitle = sellerListings.get(i)[0];
                String sellerName = sellerListings.get(i)[1];

                // Run real DSA search pipeline
                List<MatchingEngine.SearchResult> searchResults = engine.search(listingTitle);

                boolean isMatched = false;
                String canonicalProduct = "No Match Found";
                double similarity = 0.0;
                String status = "Unmatched";
                String matchType = "None";

                if (!searchResults.isEmpty()) {
                    MatchingEngine.SearchResult topResult = searchResults.get(0);
                    // Match threshold: >= 0.30 total composite score
                    if (topResult.getTotalScore() >= 0.30) {
                        isMatched = true;
                        matchedCount++;
                        canonicalProduct = topResult.getProduct().getName();
                        // Scale composite score to display similarity percentage (0-100%)
                        double rawScore = topResult.getTotalScore();
                        double normalizedSim = Math.min(100.0, Math.round((rawScore / 0.65) * 100.0));
                        similarity = Math.max(45.0, normalizedSim);
                        matchType = topResult.getMatchType();

                        if (similarity >= 75.0) {
                            highConfidenceCount++;
                            status = "High Confidence";
                        } else {
                            lowConfidenceCount++;
                            status = "Low Confidence";
                        }
                    } else {
                        status = "Unmatched";
                    }
                }

                recentMatchesJson.append("    {\n");
                recentMatchesJson.append("      \"sellerListing\": \"").append(escapeJson(listingTitle)).append("\",\n");
                recentMatchesJson.append("      \"seller\": \"").append(escapeJson(sellerName)).append("\",\n");
                recentMatchesJson.append("      \"canonicalProduct\": \"").append(escapeJson(canonicalProduct)).append("\",\n");
                recentMatchesJson.append("      \"similarity\": ").append(similarity).append(",\n");
                recentMatchesJson.append("      \"status\": \"").append(escapeJson(status)).append("\",\n");
                recentMatchesJson.append("      \"matchType\": \"").append(escapeJson(matchType)).append("\"\n");
                recentMatchesJson.append("    }");
                if (i < sellerListings.size() - 1) recentMatchesJson.append(",");
                recentMatchesJson.append("\n");
            }
            recentMatchesJson.append("  ]");

            int unmatchedCount = totalListings - matchedCount;

            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"totalProducts\": ").append(totalProducts).append(",\n");
            json.append("  \"totalListings\": ").append(totalListings).append(",\n");
            json.append("  \"matchedListings\": ").append(matchedCount).append(",\n");
            json.append("  \"unmatchedListings\": ").append(unmatchedCount).append(",\n");
            json.append("  \"highConfidence\": ").append(highConfidenceCount).append(",\n");
            json.append("  \"lowConfidence\": ").append(lowConfidenceCount).append(",\n");
            json.append("  \"recentMatches\": ").append(recentMatchesJson.toString()).append("\n");
            json.append("}");

            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    // Simple in-memory user account store
    static class UserAccount {
        String name;
        String email;
        String password;
        UserAccount(String name, String email, String password) {
            this.name = name;
            this.email = email;
            this.password = password;
        }
    }
    private static final Map<String, UserAccount> USERS_DB = new HashMap<>();
    static {
        // Pre-seeded demo user
        USERS_DB.put("demo@example.com", new UserAccount("Aakash Sharma", "demo@example.com", "password123"));
    }

    /**
     * Handler for /api/auth/signup (POST or GET)
     */
    static class AuthSignUpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> params = extractParams(exchange);
            String name = params.getOrDefault("name", "").trim();
            String email = params.getOrDefault("email", "").trim().toLowerCase();
            String password = params.getOrDefault("password", "").trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\": false, \"message\": \"Name, email and password are required.\"}");
                return;
            }

            if (USERS_DB.containsKey(email)) {
                sendJsonResponse(exchange, 400, "{\"success\": false, \"message\": \"An account with this email already exists.\"}");
                return;
            }

            USERS_DB.put(email, new UserAccount(name, email, password));
            String resp = String.format("{\"success\": true, \"message\": \"Account registered successfully!\", \"user\": {\"name\": \"%s\", \"email\": \"%s\"}}",
                    escapeJson(name), escapeJson(email));
            sendJsonResponse(exchange, 200, resp);
        }
    }

    /**
     * Handler for /api/auth/login (POST or GET)
     */
    static class AuthLoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> params = extractParams(exchange);
            String email = params.getOrDefault("email", "").trim().toLowerCase();
            String password = params.getOrDefault("password", "").trim();

            if (!USERS_DB.containsKey(email)) {
                sendJsonResponse(exchange, 401, "{\"success\": false, \"message\": \"No account found with this email.\"}");
                return;
            }

            UserAccount user = USERS_DB.get(email);
            if (!user.password.equals(password)) {
                sendJsonResponse(exchange, 401, "{\"success\": false, \"message\": \"Incorrect password. Please try again.\"}");
                return;
            }

            String resp = String.format("{\"success\": true, \"message\": \"Login successful!\", \"user\": {\"name\": \"%s\", \"email\": \"%s\"}}",
                    escapeJson(user.name), escapeJson(user.email));
            sendJsonResponse(exchange, 200, resp);
        }
    }

    /**
     * Handler for /api/auth/current
     */
    static class AuthCurrentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            sendJsonResponse(exchange, 200, "{\"status\": \"auth service active\"}");
        }
    }

    /**
     * Helper to read form or query params from exchange
     */
    private static Map<String, String> extractParams(HttpExchange exchange) throws IOException {
        Map<String, String> params = new HashMap<>();
        // Query string
        if (exchange.getRequestURI().getQuery() != null) {
            params.putAll(parseQueryParams(exchange.getRequestURI().getQuery()));
        }
        // Body (if POST form or JSON)
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try (java.io.InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (body.startsWith("{") && body.endsWith("}")) {
                    // Simple JSON key-value parser for basic auth payload
                    String clean = body.substring(1, body.length() - 1);
                    String[] entries = clean.split(",");
                    for (String entry : entries) {
                        String[] kv = entry.split(":", 2);
                        if (kv.length == 2) {
                            String k = kv[0].replaceAll("[\"\\s]", "");
                            String v = kv[1].replaceAll("^[\"\\s]+|[\"\\s]+$", "");
                            params.put(k, v);
                        }
                    }
                } else if (body.contains("=")) {
                    params.putAll(parseQueryParams(body));
                }
            }
        }
        return params;
    }

    /**
     * Static file handler serving frontend files (HTML, CSS, JS).
     */
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Look in current working directory / frontend or relative to project root
            File baseDir = new File("frontend");
            if (!baseDir.exists()) {
                // If running from backend directory
                baseDir = new File("../frontend");
            }

            File targetFile = new File(baseDir, path.replaceFirst("^/", ""));
            if (!targetFile.exists() || targetFile.isDirectory()) {
                String notFound = "404 Not Found: " + path;
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html; charset=UTF-8";
            else if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".json")) contentType = "application/json; charset=UTF-8";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";

            exchange.getResponseHeaders().set("Content-Type", contentType);
            addCorsHeaders(exchange);

            byte[] bytes = readFile(targetFile);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private byte[] readFile(File file) throws IOException {
            try (FileInputStream fis = new FileInputStream(file)) {
                return fis.readAllBytes();
            }
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                    String value = idx < pair.length() - 1
                            ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                            : "";
                    result.put(key, value);
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
