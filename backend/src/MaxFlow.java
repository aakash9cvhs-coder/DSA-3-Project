import java.util.*;

/**
 * Edmonds-Karp implementation of the Ford-Fulkerson algorithm for Maximum Flow.
 * Used for Bipartite Matching & Attribute Assignment Optimization in e-commerce:
 *
 * Problem Formulation:
 * When matching multi-attribute user queries (e.g., brand, category, storage, spec tokens)
 * against product attributes/tokens, we construct a flow network:
 * Source (s) -> Query Tokens -> Product Attribute Slots -> Sink (t).
 *
 * Edge capacities ensure each query token is matched at most once to an appropriate
 * product attribute. The maximum flow indicates the optimal matching bottleneck/cardinality,
 * providing a rigorous DSA graph-theoretic matching score.
 */
public class MaxFlow {

    private final int vertices;
    private final int[][] capacity;
    private final int[][] residualGraph;
    private final List<List<Integer>> adjacency;

    /**
     * Constructs a flow network with a given number of vertices.
     *
     * @param vertices Total number of vertices in the network
     */
    public MaxFlow(int vertices) {
        this.vertices = vertices;
        this.capacity = new int[vertices][vertices];
        this.residualGraph = new int[vertices][vertices];
        this.adjacency = new ArrayList<>();
        for (int i = 0; i < vertices; i++) {
            this.adjacency.add(new ArrayList<>());
        }
    }

    /**
     * Adds a directed capacity edge to the flow network.
     *
     * @param u   From vertex
     * @param v   To vertex
     * @param cap Edge capacity
     */
    public void addEdge(int u, int v, int cap) {
        if (u < 0 || u >= vertices || v < 0 || v >= vertices) return;
        capacity[u][v] += cap;
        residualGraph[u][v] += cap;
        adjacency.get(u).add(v);
        adjacency.get(v).add(u); // Residual backward path
    }

    /**
     * Computes the Maximum Flow from source s to sink t using Edmonds-Karp (BFS augmenting paths).
     * Time Complexity: O(V * E^2)
     *
     * @param s Source node index
     * @param t Sink node index
     * @return Maximum flow value
     */
    public int edmondsKarp(int s, int t) {
        int maxFlow = 0;
        int[] parent = new int[vertices];

        // While there is an augmenting path from source to sink in residual network
        while (bfs(s, t, parent)) {
            // Find bottleneck capacity along the path found by BFS
            int pathFlow = Integer.MAX_VALUE;
            for (int v = t; v != s; v = parent[v]) {
                int u = parent[v];
                pathFlow = Math.min(pathFlow, residualGraph[u][v]);
            }

            // Update residual capacities of edges and reverse edges
            for (int v = t; v != s; v = parent[v]) {
                int u = parent[v];
                residualGraph[u][v] -= pathFlow;
                residualGraph[v][u] += pathFlow;
            }

            maxFlow += pathFlow;
        }

        return maxFlow;
    }

    /**
     * Breadth-First Search to find the shortest augmenting path in the residual graph.
     */
    private boolean bfs(int s, int t, int[] parent) {
        Arrays.fill(parent, -1);
        parent[s] = -2; // Mark source as visited
        Queue<Integer> queue = new LinkedList<>();
        queue.add(s);

        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v : adjacency.get(u)) {
                // If vertex not visited and capacity is positive
                if (parent[v] == -1 && residualGraph[u][v] > 0) {
                    parent[v] = u;
                    if (v == t) {
                        return true; // Reached sink
                    }
                    queue.add(v);
                }
            }
        }
        return false;
    }

    /**
     * Bipartite matching helper: computes optimal token-to-attribute match flow score.
     *
     * @param queryTokens   List of normalized words from the user search
     * @param productTokens List of words/attributes from the product
     * @return Match ratio between 0.0 and 1.0 (flow / queryTokens.size())
     */
    public static double computeBipartiteMatchScore(List<String> queryTokens, List<String> productTokens) {
        if (queryTokens == null || queryTokens.isEmpty() || productTokens == null || productTokens.isEmpty()) {
            return 0.0;
        }

        int qSize = queryTokens.size();
        int pSize = productTokens.size();

        // Node indexing:
        // 0: Source (s)
        // 1 .. qSize: Query Token nodes
        // qSize + 1 .. qSize + pSize: Product Attribute nodes
        // Total nodes - 1: Sink (t)
        int source = 0;
        int sink = qSize + pSize + 1;
        int totalNodes = sink + 1;

        MaxFlow flowNetwork = new MaxFlow(totalNodes);

        // Connect source to each query token (capacity = 1)
        for (int i = 0; i < qSize; i++) {
            flowNetwork.addEdge(source, 1 + i, 1);
        }

        // Connect compatible query tokens to product tokens (capacity = 1)
        // Two tokens are compatible if exact match or high Levenshtein similarity (>= 0.75)
        for (int i = 0; i < qSize; i++) {
            String qWord = queryTokens.get(i);
            for (int j = 0; j < pSize; j++) {
                String pWord = productTokens.get(j);
                if (qWord.equals(pWord) || LevenshteinDP.computeSimilarity(qWord, pWord) >= 0.75) {
                    flowNetwork.addEdge(1 + i, 1 + qSize + j, 1);
                }
            }
        }

        // Connect each product token to sink (capacity = 1)
        for (int j = 0; j < pSize; j++) {
            flowNetwork.addEdge(1 + qSize + j, sink, 1);
        }

        int flow = flowNetwork.edmondsKarp(source, sink);
        return (double) flow / (double) qSize;
    }
}
