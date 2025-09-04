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
        List<MetricsUpsertItem> payload = dedup(items);
        client.batchUpsertMetrics(payload).block();
        log.info("Pushed {} metric snapshots", items.size());
    }

    public void applyOverrides(List<OverrideItem> items) {
        if (items == null || items.isEmpty()) return;
        client.applyOverrides(items).block();
        log.info("Applied {} overrides", items.size());
    }

    /** Compat: exécuter une réconciliation bornée si on nous passe des bornes. */
    public void runReconcile(Instant from, Instant to, int hoursWindow, boolean dryRun) {
        client.runReconcile(from == null ? null : from.toString(),
                to   == null ? null : to.toString(),
                hoursWindow, dryRun).block();
    }

    /** Nouvelle méthode: réconciliation sur l'ensemble des sources (pas de bornes temporelles). */
    public void runReconcileAll(int hoursWindow, boolean dryRun) {
        client.runReconcile(null, null, hoursWindow, dryRun).block();
    }

    /** Upsert de sources (fait le block() en interne). */
    public void batchUpsertSources(List<SourceUpsertItem> items) {
        if (items == null || items.isEmpty()) return;
        client.batchUpsertSources(items).block();
    }

    /** Upsert de métriques (avec dédup si tu l’as déjà en privé). */
    public void batchUpsertMetrics(List<MetricsUpsertItem> items) {
        if (items == null || items.isEmpty()) return;
        // si tu as une méthode dedup(List<MetricsUpsertItem>) privée, garde-la :
        // var payload = dedup(items);
        var payload = items;
        client.batchUpsertMetrics(payload).block();
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
