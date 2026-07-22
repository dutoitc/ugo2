package ch.mno.ugo2.service;

import ch.mno.ugo2.config.AppProps;
import ch.mno.ugo2.util.SensitiveDataRedactor;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Log
@Service
@RequiredArgsConstructor
public class BatchOrchestrator {

    private final AppProps cfg;
    private final DiscoveryService discoveryService;
    private final WebApiSinkService webApiSinkService;

    /**
     * Une exécution collecte les plateformes, réconcilie, puis rafraîchit les vues
     * matérialisées une seule fois en fin de batch.
     */
    public void run() {
        String runId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        reportBatch(runId, "RUNNING", startedAt, null, null, null, null);

        int pushedSnapshots = 0;
        try {
            pushedSnapshots = discoveryService.discover();
            int finalPushedSnapshots = pushedSnapshots;
            log.info(() -> String.format(
                    "[batch] discovery done, pushedSnapshots=%d — launching API reconciliation…",
                    finalPushedSnapshots
            ));

            int hoursWindow = cfg.getBatch().hoursWindow;
            webApiSinkService.runReconcileAll(hoursWindow, false);
            webApiSinkService.refreshMaterializedViews();

            int durationMs = elapsedMs(startedNanos);
            reportBatch(runId, "SUCCESS", startedAt, Instant.now(), durationMs, pushedSnapshots, null);
            log.info(() -> String.format(
                    "[batch] completed, snapshots=%d, durationMs=%d",
                    finalPushedSnapshots,
                    durationMs
            ));
        } catch (RuntimeException e) {
            int durationMs = elapsedMs(startedNanos);
            reportBatch(runId, "ERROR", startedAt, Instant.now(), durationMs, pushedSnapshots,
                    SensitiveDataRedactor.redact(e));
            throw e;
        }
    }

    private void reportBatch(String runId, String status, Instant startedAt, Instant finishedAt,
                             Integer durationMs, Integer items, String message) {
        try {
            webApiSinkService.reportBatch(
                    runId, status, startedAt, finishedAt, durationMs, items, message
            );
        } catch (Exception e) {
            log.warning("[batch] health report failed: " + SensitiveDataRedactor.redact(e));
        }
    }

    private static int elapsedMs(long startedNanos) {
        return (int)Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
