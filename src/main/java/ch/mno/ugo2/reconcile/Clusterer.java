package ch.mno.ugo2.reconcile;

import ch.mno.ugo2.model.SourceVideo;
import java.util.*;

public class Clusterer {
  private final double threshold;
  public Clusterer(double threshold) { this.threshold = threshold; }

  public List<List<SourceVideo>> cluster(List<SourceVideo> items, MatchingScorer scorer) {
    int n = items.size();
    boolean[] visited = new boolean[n];
    List<List<SourceVideo>> clusters = new ArrayList<>();

    for (int i = 0; i < n; i++) {
      if (visited[i]) continue;
      List<Integer> compIdx = new ArrayList<>();
      Deque<Integer> dq = new ArrayDeque<>();
      dq.add(i);
      visited[i] = true;
      while (!dq.isEmpty()) {
        int u = dq.poll();
        compIdx.add(u);
        for (int v = 0; v < n; v++) {
          if (!visited[v] && u != v) {
            double s = scorer.score(items.get(u), items.get(v));
            if (s >= threshold) {
              visited[v] = true;
              dq.add(v);
            }
          }
        }
      }
      List<SourceVideo> cluster = new ArrayList<>(compIdx.size());
      for (int idx : compIdx) cluster.add(items.get(idx));
      clusters.add(cluster);
    }
    return clusters;
  }
}
