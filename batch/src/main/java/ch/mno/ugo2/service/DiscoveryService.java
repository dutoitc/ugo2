package ch.mno.ugo2.service;

import ch.mno.ugo2.config.AppProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Découverte des nouvelles sources côté plateformes.
 * Version “API sink”: plus d’accès DB local.
 * Pour l’instant, on ne pousse rien ici (le push se fera via le collecteur qui appelle l’API Web).
 * On retourne juste 0 pour garder la signature attendue par BatchOrchestrator.
 */
@Service
public class DiscoveryService {

  private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);
  private final AppProps cfg;

  public DiscoveryService(AppProps cfg) {
    this.cfg = cfg;
  }

  /**
   * Découverte (initiale ou incrémentale).
   * @param initial si true, scan large “initial”
   * @return nombre de sources nouvellement détectées (0 tant que la collecte pousse directement vers l’API)
   */
  public int discover(boolean initial) {
    // Ici on pourrait :
    // - interroger FB/YT/IG/WP pour lister les contenus,
    // - construire des DTO "SourceUpsertItem",
    // - et les envoyer à l’API via WebApiSinkService.
    // Pour l’instant on ne fait rien (pas de repo local).
    log.info("[discovery] initial={} (no-op, API sink mode)", initial);
    return 0;
  }
}
