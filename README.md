# NexusMatch - E-Commerce Entity Matcher & Search Engine (DSA-3 Project)

A complete e-commerce entity matching and search application built from scratch for DSA-3, powered by custom data structures and algorithms with zero third-party dependencies.

---

## 🏗️ Architecture & DSA Components

| DSA Component | Java Class | Complexity | Role in Application |
|---|---|---|---|
| **Text Normalizer** | `TextNormalizer.java` | $O(N)$ | Unicode case folding, special symbol stripping, and whitespace-token stream extraction. |
| **Trie (Prefix Tree)** | `Trie.java` | $O(L)$ query | Fast prefix indexing of product catalog tokens and real-time autocomplete suggestions. |
| **Rabin-Karp** | `RabinKarp.java` | $O(N + M)$ avg | Polynomial rolling hash ($BASE=256$, $PRIME=10^9+7$) for exact phrase/substring detection. |
| **Levenshtein DP** | `LevenshteinDP.java` | $O(N \cdot M)$ | Bottom-up 2D Dynamic Programming table for edit distance, typo correction, and fuzzy matching. |
| **Max Flow (Edmonds-Karp)**| `MaxFlow.java` | $O(V \cdot E^2)$ | Bipartite attribute assignment network matching user multi-spec query tokens to product attributes. |
| **Matching Engine** | `MatchingEngine.java` | Composite | Blends prefix, substring, edit distance, and flow scores into a normalized relevance rank. |
| **Web Service** | `WebSearchService.java`| Built-in HTTP | Zero-dependency embedded Java HTTP server serving REST API and the frontend client. |
| **CLI & Test Runner** | `Main.java` | Interactive | Automated algorithm test harness and interactive terminal search interface. |

---

## 📂 Project Structure

```text
EcommerceEntityMatcher(DSA-3)/
│
├── backend/
│   ├── src/
│   │   ├── Main.java
│   │   ├── TextNormalizer.java
│   │   ├── Trie.java
│   │   ├── RabinKarp.java
│   │   ├── LevenshteinDP.java
│   │   ├── MaxFlow.java
│   │   ├── MatchingEngine.java
│   │   └── WebSearchService.java
│   └── bin/                   (compiled .class bytecode)
│
├── frontend/
│   ├── index.html             (Modern responsive search UI)
│   ├── style.css              (Glassmorphic dark design system)
│   └── script.js              (Trie autocomplete & search client)
│
└── README.md
```

---

## 🚀 How to Compile and Run in VS Code

### Step 1: Open the Project in VS Code
Open this folder in VS Code:
```text
File -> Open Folder... -> Select "EcommerceEntityMatcher(DSA-3)"
```

### Step 2: Open a Terminal in VS Code
Press ``Ctrl + ` `` (Backtick) or go to **Terminal -> New Terminal** (PowerShell or Bash).

### Step 3: Compile the Backend
Navigate to the `backend` folder and compile all Java files into `bin/`:
```powershell
cd backend
javac -d bin src/*.java
```

### Step 4: Run the Application
Run `Main.java`:
```powershell
java -cp bin Main
```

When started, the application will:
1. Run automated self-tests verifying Text Normalization, Trie, Rabin-Karp, Levenshtein DP, and Max Flow.
2. Start the embedded web server at **`http://localhost:8080/`**.
3. Open an interactive CLI search prompt in the terminal.

---

## 🌐 Opening the Web Interface

Once the server is running, open your web browser and visit:
👉 **[http://localhost:8080/](http://localhost:8080/)**

You can also open `frontend/index.html` directly in any browser (the frontend includes automatic offline simulation fallback).

### Sample Test Queries to Try in the UI & CLI:
- **`iphone`** — Exact substring match ($1.00$ score)
- **`samung galxy`** — Levenshtein DP typo correction & fuzzy tolerance
- **`noise cancelling`** — Rabin-Karp polynomial rolling hash phrase detection
- **`running shoes`** — Edmonds-Karp bipartite flow attribute matching
- **`macbook`** — Trie prefix suggestion and fast lookup

---

## 🌐 Web Search API Key (Live E-Commerce Search)

NexusMatch supports searching live products across the web using a **SerpApi Key** (Google Shopping / Amazon India / Flipkart):
1. In the Web UI, open the **Web Search API** section in the sidebar or click the **🔑 API Key** button on the Products page.
2. Enter your [SerpApi Key](https://serpapi.com) and click **Save & Enable**.
3. Toggle **🌐 Search Live Web** to search live products from Google Shopping and rank every result through the DSA-3 pipeline (Trie, Levenshtein, Rabin-Karp, Edmonds-Karp).
4. Direct API format: `http://localhost:8080/api/online-search?q=laptop&apiKey=YOUR_API_KEY`
