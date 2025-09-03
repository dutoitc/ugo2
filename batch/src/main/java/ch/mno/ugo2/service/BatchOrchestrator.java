package ch.mno.ugo2.service;

import ch.mno.ugo2.config.AppProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@Log
@RequiredArgsConstructor
public class BatchOrchestrator {

    private final AppProps cfg;
    private final DiscoveryService discoveryService;
    private final WebApiSinkService webApiSinkService;

    /**
     * Pipeline unique:
     *  1) Discover (YouTube + Facebook): upsert des sources + métriques (baseline + courant)
     *  2) Réconciliation côté API: crée/lie video_id sur la fenêtre glissante
     */
    public void run() {
        final int days = cfg.getBatch().getRollingDays();
        final int hoursWindow = cfg.getBatch().getHoursWindow();

        var to   = LocalDateTime.now();
        var from = to.minusDays(days);

        log.info(() -> String.format("[batch] start window=%s .. %s (days=%d)", from, to, days));

        int pushedSnapshots = discoveryService.discover();
        log.info(() -> String.format("[batch] discovery done, pushedSnapshots=%d — launching API reconciliation…", pushedSnapshots));

        // Réconciliation côté API (DB en ligne) — crée/lie video_id
        webApiSinkService.runReconcile(
                from.atZone(ZoneOffset.UTC).toInstant(),
                to  .atZone(ZoneOffset.UTC).toInstant(),
                hoursWindow,
                false // dryRun=false => applique les liens video_id
        );

        log.info("[batch] reconcile (API sink) triggered");
    }
}
