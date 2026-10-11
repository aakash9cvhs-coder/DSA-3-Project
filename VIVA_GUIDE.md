# Viva & Project Defense Guide: DSA-3 E-Commerce Entity Matcher

**Degree:** 2nd Year B.Tech / B.E. (Computer Science & Engineering)  
**Subject:** Data Structures & Algorithms - III (DSA-3)  
**Project Title:** NexusMatch - Algorithmic E-Commerce Product Entity Matcher & Search Engine  
**Tech Stack:** Core Java (Zero external DSA dependencies), Vanilla JavaScript, HTML5, CSS3, Embedded Java HTTP Server  

---

## 🎯 1. Project Overview (Elevator Pitch for Examiners)
> *"NexusMatch solves the real-world e-commerce entity matching problem: how to match imperfect, noisy, or misspelled user search queries against a catalog of product entities in real-time. Instead of treating search as a simple SQL `LIKE` query or relying on third-party black-box libraries, our system implements four foundational DSA paradigms from scratch in pure Java:*
> 1. *Prefix Tree (**Trie**) for $O(L)$ keystroke autocomplete & prefix indexing.*
> 2. *Polynomial Rolling Hash (**Rabin-Karp**) for exact phrase substring matching.*
> 3. *2D Dynamic Programming (**Levenshtein Distance**) for typo tolerance & fuzzy matching.*
> 4. *Network Flow / Graph Theory (**Edmonds-Karp Max Flow**) for multi-attribute bipartite matching.*
> *The backend ranks products through a composite scoring function and serves results via an embedded REST API to a modern, Indian Rupees (₹ INR)-localized client interface."*

---

## 🔬 2. Algorithm Breakdown & Viva Explanations

### A. Trie (Prefix Tree) — [Trie.java](file:///c:/Users/aakas/OneDrive/Desktop/EcommerceEntityMatcher(DSA-3)/backend/src/Trie.java)
- **Problem Solved:** Instant search suggestions as the user types each character, and prefix lookup.
- **Time Complexity:**
  - Insertion: $O(L)$ where $L$ is the length of the word.
  - Search / Autocomplete: $O(P + K)$ where $P$ is prefix length and $K$ is the number of child nodes traversed.
- **Space Complexity:** $O(\Sigma \cdot N \cdot L)$ where $\Sigma$ is alphabet size.
- **Examiner Question:** *Why use a Trie over a HashMap for autocomplete?*
  - **Answer:** *"A HashMap with key `prefix` would require storing every prefix of every word, causing exponential space overhead. A Trie shares common prefixes among words (e.g., 'apple', 'app', 'application' share 'a-p-p'), saving memory and allowing ordered DFS traversal to retrieve suggestions in lexicographical order."*

---

### B. Rabin-Karp Substring Matching — [RabinKarp.java](file:///c:/Users/aakas/OneDrive/Desktop/EcommerceEntityMatcher(DSA-3)/backend/src/RabinKarp.java)
- **Problem Solved:** Detecting exact multi-word search phrases (e.g., `"noise cancelling"`, `"air zoom"`) inside long product descriptions.
- **Core Formula (Rolling Hash):**
  $$\text{Hash}(S[i+1 \dots i+m]) = \left( \text{BASE} \cdot (\text{Hash}(S[i \dots i+m-1]) - S[i] \cdot h) + S[i+m] \right) \pmod{\text{MOD}}$$
  - $\text{BASE} = 256$ (ASCII base)
  - $\text{MOD} = 1,000,000,007$ (Large prime to prevent hash collisions)
  - $h = \text{BASE}^{m-1} \pmod{\text{MOD}}$
- **Time Complexity:**
  - Average Case: $O(N + M)$
  - Worst Case (Spurious hits): $O(N \cdot M)$
- **Examiner Question:** *What is a spurious hit and how do you handle it?*
  - **Answer:** *"A spurious hit occurs when two different substrings produce the identical hash value modulo $10^9+7$ due to collision. In `RabinKarp.java`, whenever hashes match, we perform a character-by-character verification before declaring a true match."*

---

### C. Levenshtein Dynamic Programming — [LevenshteinDP.java](file:///c:/Users/aakas/OneDrive/Desktop/EcommerceEntityMatcher(DSA-3)/backend/src/LevenshteinDP.java)
- **Problem Solved:** Typo tolerance (e.g., user types `"samung galxy"`, `"aple iphon"`).
- **Recurrence Relation:**
  $$\text{dp}[i][j] = \begin{cases} 
  \text{dp}[i-1][j-1] & \text{if } s_1[i-1] == s_2[j-1] \\ 
  1 + \min(\text{dp}[i-1][j], \text{dp}[i][j-1], \text{dp}[i-1][j-1]) & \text{if } s_1[i-1] \neq s_2[j-1] 
  \end{cases}$$
- **Normalized Similarity Formula:**
  $$\text{Similarity} = 1.0 - \frac{\text{EditDistance}(s_1, s_2)}{\max(\text{len}(s_1), \text{len}(s_2))}$$
- **Time Complexity:** $O(N \cdot M)$ where $N, M$ are lengths of the strings.
- **Space Complexity:** $O(N \cdot M)$ using a 2D DP matrix.

---

### D. Edmonds-Karp Maximum Flow — [MaxFlow.java](file:///c:/Users/aakas/OneDrive/Desktop/EcommerceEntityMatcher(DSA-3)/backend/src/MaxFlow.java)
- **Problem Solved:** Bipartite attribute matching optimization.
- **Graph Construction:**
  - **Source ($s$)** connected to each Query Token ($Q_i$) with capacity $1$.
  - **Query Tokens ($Q_i$)** connected to Product Attributes/Tokens ($P_j$) with capacity $1$ if compatible (Exact match or Levenshtein similarity $\ge 0.75$).
  - **Product Tokens ($P_j$)** connected to **Sink ($t$)** with capacity $1$.
- **Algorithm Used:** Ford-Fulkerson implemented with **BFS (Edmonds-Karp)** to find shortest augmenting paths in the residual network.
- **Time Complexity:** $O(V \cdot E^2)$ where $V = |Q| + |P| + 2$.
- **Examiner Question:** *Why use Max Flow for search in a 2nd-year project?*
  - **Answer:** *"When a user inputs multiple specifications like 'apple titanium pro phone', greedy keyword matching can assign the same product attribute twice or match sub-optimally. By modeling query-to-product mapping as a maximum cardinality bipartite matching problem via Edmonds-Karp, we mathematically guarantee the optimal one-to-one attribute assignment bottleneck."*

---

## 📊 3. Composite Relevance Scoring Formula
In [MatchingEngine.java](file:///c:/Users/aakas/OneDrive/Desktop/EcommerceEntityMatcher(DSA-3)/backend/src/MatchingEngine.java):

$$\text{Final Score} = (0.35 \times S_{\text{RabinKarp}}) + (0.30 \times S_{\text{Levenshtein}}) + (0.25 \times S_{\text{MaxFlow}}) + (0.10 \times S_{\text{Trie}}) + \text{Rating Boost}$$

- **Rabin-Karp ($35\%$)**: Highest priority for exact phrases.
- **Levenshtein ($30\%$)**: Rewards phonetic/typo similarity.
- **Max Flow ($25\%$)**: Rewards multi-attribute constraint satisfaction.
- **Trie ($10\%$)**: Rewards prefix matches.
- **Rating Boost**: Small tie-breaker boost based on product quality ($+0.02 \times \frac{\text{Rating}}{5.0}$).

---

## 💻 4. System Architecture Diagram

```text
[User Web Browser] 
       │  (HTTP / JSON - Localhost:8080)
       ▼
[WebSearchService.java (Embedded HttpServer)]
       │
       ├── /api/auth/login, /api/auth/signup  ──> [In-Memory User DB]
       │
       ├── /api/suggest?q=...                ──> [Trie.java (Prefix Autocomplete)]
       │
       └── /api/search?q=...                 ──> [MatchingEngine.java]
                                                       │
                     ┌─────────────────────────────────┼──────────────────────────────┐
                     ▼                                 ▼                              ▼
             [TextNormalizer]                   [RabinKarp.java]             [MaxFlow.java]
            (Tokens & Cleaning)                (Rolling Hash Substring)      (Edmonds-Karp Flow)
                     │                                 │                              │
                     └───────────────────┬─────────────┴──────────────────────────────┘
                                         ▼
                                [LevenshteinDP.java]
                              (Dynamic Programming Matrix)
                                         │
                                         ▼
                              [Ranked Product Results]
```

---

## ❓ 5. Common External Examiner Viva Questions & Answers

| # | Question | Ideal Student Answer |
|---|---|---|
| **Q1** | **Why didn't you use MySQL/PostgreSQL with `LIKE '%term%'`?** | SQL `LIKE` performs costly $O(N)$ full table scans and fails on misspellings (e.g., `'samung'` returns 0 results). Our custom DSA pipeline integrates typo tolerance via DP and prefix caching via Trie in microsecond time. |
| **Q2** | **Why choose Edmonds-Karp over DFS-based Ford-Fulkerson?** | Standard Ford-Fulkerson uses DFS which can run in $O(E \cdot \text{max\_flow})$ and may take worst-case paths with arbitrary capacities. Edmonds-Karp uses BFS to guarantee the shortest augmenting path, giving a polynomial upper bound of $O(V \cdot E^2)$. |
| **Q3** | **How do you prevent integer overflow in Rabin-Karp?** | In Java, when subtracting the leading character contribution `(text.charAt(i) * h) % PRIME_MOD`, the result can be negative. We add `+ PRIME_MOD` before taking `% PRIME_MOD` so the rolling hash always stays in $[0, \text{PRIME\_MOD}-1]$. |
| **Q4** | **What is the space complexity of your Levenshtein DP?** | It uses an $(N+1) \times (M+1)$ matrix, so space is $O(N \cdot M)$. As an optimization, it could be reduced to $O(2 \times \min(N, M))$ by keeping only the previous row. |
| **Q5** | **How is this project suitable for 2nd year?** | It covers foundational 2nd year CS curricula: **Trees (Trie)**, **String Matching (Rabin-Karp)**, **Dynamic Programming (Levenshtein)**, and **Advanced Graphs / Network Flow (Max Flow)**, showing their practical composition in a working full-stack application. |
