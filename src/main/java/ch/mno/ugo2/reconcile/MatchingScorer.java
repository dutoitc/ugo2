package ch.mno.ugo2.reconcile;

import ch.mno.ugo2.config.AppProps;
import ch.mno.ugo2.model.SourceVideo;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;

import java.time.Duration;

public class MatchingScorer {
  private final AppProps.Reconcile cfg;
  private final JaroWinklerSimilarity jw = new JaroWinklerSimilarity();

  public MatchingScorer(AppProps.Reconcile cfg) {
    this.cfg = cfg;
  }

  public double score(SourceVideo a, SourceVideo b) {
    String at = TextUtil.norm(a.getTitle());
    String bt = TextUtil.norm(b.getTitle());
    double titleSim = jw.apply(at, bt);

    String ad = TextUtil.norm(a.getDescription());
    String bd = TextUtil.norm(b.getDescription());
    double descSim = ad.isEmpty() || bd.isEmpty() ? 0.0 : jw.apply(ad, bd);

    double base = cfg.titleWeight * titleSim + cfg.descWeight * descSim;

    if (a.getPublishedAt() != null && b.getPublishedAt() != null) {
      long hours = Math.abs(Duration.between(a.getPublishedAt(), b.getPublishedAt()).toHours());
      if (hours <= cfg.timeBonusHours) {
        double bonus = cfg.timeWeight * (1.0 - ((double)hours / cfg.timeBonusHours));
        if (bonus > 0) base += bonus;
      }
    }

    return Math.max(0.0, Math.min(1.0, base));
  }
}
