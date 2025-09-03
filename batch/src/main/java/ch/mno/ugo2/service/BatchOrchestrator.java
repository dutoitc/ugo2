package ch.mno.ugo2.service;

import ch.mno.ugo2.config.AppProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;

@Log
@Service
@RequiredArgsConstructor
public class BatchOrchestrator {

    private final AppProps cfg;
    private final DiscoveryService discoveryService;
    private final WebApiSinkService webApiSinkService;

    /**
     * Nouvelle règle: batch:run charge TOUT l'historique (pas de fenêtre glissante).
     * Puis on déclenche la réconciliation côté API sur TOUTES les sources
     * (from/to non renseignés) avec un "hoursWindow" issu de la config.
     */
    public void run() {
        int pushedSnapshots = discoveryService.discover();
        log.info(() -> String.format("[batch] discovery done, pushedSnapshots=%d — launching API reconciliation…", pushedSnapshots));

        // Réconciliation côté API (DB en ligne) — crée/lie video_id sur l'ensemble des sources
        int hoursWindow = cfg.getBatch().hoursWindow;
        webApiSinkService.runReconcileAll(hoursWindow, false);

        log.info("[batch] reconcile (API sink) triggered for ALL sources (no time window)");
    }
}
