package ch.mno.ugo2.service;

import ch.mno.ugo2.api.WebApiClient;
import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.OverrideItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebApiSinkService {

    private final WebApiClient client;

    public void batchUpsertSources(List<SourceUpsertItem> sources) {
        if (sources == null || sources.isEmpty()) return;
        client.batchUpsertSources(sources).block();
        log.info("Ingested {} sources", sources.size());
    }

    public void pushSources(List<SourceUpsertItem> items) {
        batchUpsertSources(items);
    }

    public void batchUpsertMetrics(List<MetricsUpsertItem> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return;
        client.batchUpsertMetrics(snapshots).block();
        log.info("Ingested {} metric snapshots", snapshots.size());
    }

    public void pushMetrics(List<MetricsUpsertItem> items) {
        batchUpsertMetrics(items);
    }

    public void applyOverrides(List<OverrideItem> items) {
        if (items == null || items.isEmpty()) return;
        client.applyOverrides(items).block();
        log.info("Applied {} overrides", items.size());
    }

    public void runReconcile(Instant from, Instant to, int hoursWindow, boolean dryRun) {
        client.runReconcile(
                from == null ? null : from.toString(),
                to == null ? null : to.toString(),
                hoursWindow,
                dryRun
        ).block();
    }

    public void runReconcileAll(int hoursWindow, boolean dryRun) {
        client.runReconcile(null, null, hoursWindow, dryRun).block();
    }

    public void reportPlatformHealth(String platform, boolean success, int durationMs, int items,
                                     String message, String tokenExpiresAt, boolean tokenLikelyExpired) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "platform");
        event.put("platform", platform);
        event.put("status", success ? "SUCCESS" : "ERROR");
        event.put("duration_ms", Math.max(0, durationMs));
        event.put("items", Math.max(0, items));
        if (message != null && !message.isBlank()) event.put("message", message);
        if (tokenExpiresAt != null && !tokenExpiresAt.isBlank()) event.put("token_expires_at", tokenExpiresAt);
        event.put("token_likely_expired", tokenLikelyExpired);
        client.reportHealth(event).block();
    }

    public void reportBatch(String runId, String status, Instant startedAt, Instant finishedAt,
                            Integer durationMs, Integer items, String message) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "batch");
        event.put("run_id", runId);
        event.put("status", status);
        event.put("started_at", startedAt.toString());
        if (finishedAt != null) event.put("finished_at", finishedAt.toString());
        if (durationMs != null) event.put("duration_ms", durationMs);
        if (items != null) event.put("items", items);
        if (message != null && !message.isBlank()) event.put("message", message);
        client.reportHealth(event).block();
    }

    public void refreshMaterializedViews() {
        client.refreshMaterializedViews().block();
    }

    List<MetricsUpsertItem> dedup(List<MetricsUpsertItem> in) {
        Map<String, MetricsUpsertItem> uniq = new LinkedHashMap<>();
        for (var m : in) {
            if (m == null) continue;
            String idPart = m.getSource_video_id() != null
                    ? String.valueOf(m.getSource_video_id())
                    : (m.getPlatform() + ":" + String.valueOf(m.getPlatform_video_id()));
            String tPart = m.getSnapshot_at() != null ? m.getSnapshot_at().toString() : "";
            String key = (m.getPlatform() == null ? "" : m.getPlatform()) + "|" + idPart + "|" + tPart;
            uniq.put(key, m);
        }
        return List.copyOf(uniq.values());
    }
}
