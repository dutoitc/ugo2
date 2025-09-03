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

    public void run(boolean initial) {
        final int days = initial ? cfg.getBatch().getInitialDays() : cfg.getBatch().getRollingDays();
        final int hoursWindow = cfg.getBatch().getHoursWindow();

        var to   = LocalDateTime.now();
        var from = to.minusDays(days);

        log.info(() -> String.format("[batch] start initial=%s window=%s..%s days=%d",
                initial, from, to, days));

        int created = discoveryService.discover(initial);
        log.info(() -> String.format("[batch] ingest done createdSources=%d — launching API reconciliation…", created));

        // Réconciliation côté API (DB en ligne)
        webApiSinkService.runReconcile(
                from.atZone(ZoneOffset.UTC).toInstant(),
                to.atZone(ZoneOffset.UTC).toInstant(),
                hoursWindow,
                false // dryRun=false => applique les liens video_id
        );

        log.info("[batch] reconcile (API sink) triggered");
    }
}
