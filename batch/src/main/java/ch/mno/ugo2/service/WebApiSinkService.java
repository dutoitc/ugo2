package ch.mno.ugo2.service;

import ch.mno.ugo2.api.WebApiClient;
import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.OverrideItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebApiSinkService {
    private final WebApiClient client;

    public void pushSources(List<SourceUpsertItem> items) {
        if (items == null || items.isEmpty()) return;
        client.batchUpsertSources(items).block();
        log.info("Pushed {} sources", items.size());
    }
    public void pushMetrics(List<MetricsUpsertItem> items) {
        if (items == null || items.isEmpty()) return;
        client.batchUpsertMetrics(items).block();
        log.info("Pushed {} metric snapshots", items.size());
    }
    public void applyOverrides(List<OverrideItem> items) {
        if (items == null || items.isEmpty()) return;
        client.applyOverrides(items).block();
        log.info("Applied {} overrides", items.size());
    }
}
