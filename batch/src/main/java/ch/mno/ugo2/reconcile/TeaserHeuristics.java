package ch.mno.ugo2.reconcile;

import ch.mno.ugo2.config.AppProps;
import ch.mno.ugo2.model.SourceVideo;

public class TeaserHeuristics {
//  private final AppProps.Reconcile cfg;
//  public TeaserHeuristics(AppProps.Reconcile cfg) { this.cfg = cfg; }
//
//  public boolean isTeaser(SourceVideo s, long clusterEarliestEpochSec) {
//    String t = TextUtil.norm(s.getTitle());
//    for (String k : cfg.teaserKeywords) {
//      if (t.contains(k)) return true;
//    }
//    if (s.getDurationSeconds() != null && s.getDurationSeconds() > 0 && s.getDurationSeconds() <= cfg.teaserMaxSeconds) {
//      return true;
//    }
//    if (s.getPublishedAt() != null) {
//      long diffSec = (s.getPublishedAt().toEpochSecond(java.time.ZoneOffset.UTC) - clusterEarliestEpochSec);
//      if (diffSec < 0 && Math.abs(diffSec) >= 2L*24*3600) {
//        return true;
//      }
//    }
//    return false;
//  }
}
