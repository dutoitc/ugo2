package ch.mno.ugo2.service;

import ch.mno.ugo2.config.AppProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Réconciliation “API sink”: le regroupement se fait côté API/DB web.
 * Ici on garde la signature pour l’orchestrateur, mais on ne touche plus la DB locale.
 */
@Service
public class ReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
  private final AppProps cfg;

  public ReconciliationService(AppProps cfg) {
    this.cfg = cfg;
  }

  /**
   * Résumé de réconciliation attendu par BatchOrchestrator.
   * Garder ces noms d’accesseurs: clusters(), linkedSources(), skippedLocked()
   * car l’orchestrateur les utilise dans son printf.
   */
  public record ResultSummary(int clusters, int created, int linkedSources, int skippedLocked) {}

  /**
   * @param from fenêtre de temps (début)
   * @param to   fenêtre de temps (fin)
   * @param dryRun si true, ne ferait rien (ici no-op de toute façon)
   * @return un résumé “vide” (0) tant que tout se passe côté API web.
   */
  public ResultSummary reconcile(LocalDateTime from, LocalDateTime to, boolean dryRun) {
    log.info("[reconcile] window={}..{}, dryRun={}, mode=API-sink (no-op ici)", from, to, dryRun);
    // Si tu veux déclencher un recalcul côté API web, c’est ici que tu appellerais un endpoint dédié.
    return new ResultSummary(0, 0, 0, 0);
  }
}
