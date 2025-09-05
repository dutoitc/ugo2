package ch.mno.ugo2.service;

import ch.mno.ugo2.api.WebApiClient;
import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.OverrideItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class WebApiSinkService {

    private final WebApiClient client;

    /* ---------- SOURCES ---------- */

    public void batchUpsertSources(List<SourceUpsertItem> sources) {
        var payload = Map.of("sources", sources);
        log.info("API POST /api/v1/sources:batchUpsert (items={}, bytes={})", sources.size(), approxBytes(payload));
        String body = client.postJsonForBody("/api/v1/sources:batchUpsert", payload).block();
        log.info("Ingest sources result: {}", body);
    }

    public void pushSources(List<SourceUpsertItem> items) { batchUpsertSources(items); }

    /* ---------- METRICS ---------- */

    public void batchUpsertMetrics(List<MetricsUpsertItem> snapshots) {
        var payload = Map.of("snapshots", snapshots);
        log.info("API POST /api/v1/metrics:batchUpsert (items={}, bytes={})", snapshots.size(), approxBytes(payload));
        String body = client.postJsonForBody("/api/v1/metrics:batchUpsert", payload).block();
        log.info("Ingest metrics result: {}", body);
    }


    public void pushMetrics(List<MetricsUpsertItem> items) { batchUpsertMetrics(items); }

    private long approxBytes(Object obj) {
        try {
            String s = obj.toString();
            return s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        } catch (Exception e) { return -1; }
    }

    /* ---------- OVERRIDES ---------- */

    public void applyOverrides(List<OverrideItem> items) {
        if (items == null || items.isEmpty()) return;
        client.applyOverrides(items).block();
        log.info("Applied {} overrides", items.size());
    }

    /* ---------- RECONCILE ---------- */

    public void runReconcile(Instant from, Instant to, int hoursWindow, boolean dryRun) {
        client.runReconcile(from == null ? null : from.toString(),
                to == null ? null : to.toString(),
                hoursWindow, dryRun).block();
    }

    public void runReconcileAll(int hoursWindow, boolean dryRun) {
        client.runReconcile(null, null, hoursWindow, dryRun).block();
    }

    /* ---------- Helpers ---------- */

    List<MetricsUpsertItem> dedup(List<MetricsUpsertItem> in) {
        Map<String, MetricsUpsertItem> uniq = new LinkedHashMap<>();
        for (var m : in) {
            if (m == null) continue;
            String idPart = m.getSource_video_id() != null
                    ? String.valueOf(m.getSource_video_id())
                    : (m.getPlatform() + ":" + String.valueOf(m.getPlatform_video_id()));
            String tPart = m.getSnapshot_at() != null ? m.getSnapshot_at().toString() : "";
            String k = (m.getPlatform()==null?"":m.getPlatform()) + "|" + idPart + "|" + tPart;
            uniq.put(k, m);
        }
        return new ArrayList<>(uniq.values());
    }


}
