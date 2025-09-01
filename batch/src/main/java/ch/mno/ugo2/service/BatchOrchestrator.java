package ch.mno.ugo2.service;

import ch.mno.ugo2.config.AppProps;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BatchOrchestrator {

    private final AppProps cfg;
    private final DiscoveryService discoveryService;
    private final ReconciliationService reconciliationService;

    public BatchOrchestrator(AppProps cfg, DiscoveryService discoveryService, ReconciliationService reconciliationService) {
        this.cfg = cfg;
        this.discoveryService = discoveryService;
        this.reconciliationService = reconciliationService;
    }
    public void run(boolean initial) {
        int created = discoveryService.discover(initial);
        var from = LocalDateTime.now().minusDays(cfg.getBatch().rollingDays);
        var to = LocalDateTime.now();
        var res = reconciliationService.reconcile(from, to, false);
        System.out.printf("[batch] initial=%s created=%d clusters=%d linked=%d skippedLocked=%d%n",
                initial, created, res.clusters(), res.linkedSources(), res.skippedLocked());
    }
}
