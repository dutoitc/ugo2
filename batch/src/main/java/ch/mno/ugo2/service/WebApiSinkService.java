package ch.mno.ugo2.service;

import ch.mno.ugo2.api.WebApiClient;
import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.OverrideItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        var payload = dedup(items);
        int suppressed = items.size() - payload.size();
        if (suppressed > 0) log.warn("pushMetrics: {} duplicates supprimés (même platform+source+captured_at)", suppressed);
        client.batchUpsertMetrics(payload).block();
        log.info("Pushed {} metric snapshots", items.size());
    }
    public void applyOverrides(List<OverrideItem> items) {
        if (items == null || items.isEmpty()) return;
        client.applyOverrides(items).block();
        log.info("Applied {} overrides", items.size());
    }

    public void runReconcile(Instant from, Instant to, int hoursWindow, boolean dryRun) {
        client.runReconcile(from.toString(), to.toString(), hoursWindow, dryRun).block();
    }
    List<MetricsUpsertItem> dedup(List<MetricsUpsertItem> in) {
        Map<String, MetricsUpsertItem> uniq = new LinkedHashMap<>();
        for (var m : in) {
            if (m == null) continue;
            String k = (m.getPlatform()==null?"":m.getPlatform()) + "|" +
                    (m.getPlatform_source_id()==null?"":m.getPlatform_source_id()) + "|" +
                    (m.getCaptured_at()==null?"":m.getCaptured_at());
            uniq.put(k, m); // garde le dernier si collision
        }
        return new ArrayList<>(uniq.values());
    }

}
