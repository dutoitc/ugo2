// batch/src/main/java/ch/mno/ugo2/facebook/FacebookCollectorService.java
package ch.mno.ugo2.facebook;

import ch.mno.ugo2.config.FacebookProps;
import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import ch.mno.ugo2.facebook.responses.FacebookPostsResponse;
import ch.mno.ugo2.facebook.responses.InsightMetric;
import ch.mno.ugo2.facebook.responses.InsightsResponse;
import ch.mno.ugo2.facebook.responses.VideoResponse;
import ch.mno.ugo2.service.WebApiSinkService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacebookCollectorService {

    private final FacebookClient fb;
    private final FacebookProps cfg;
    private final WebApiSinkService sink;

    /** Collecte multi-pages + upsert sources + upsert metrics. */
    public int collect() {
        Set<String> allVideoIds = new LinkedHashSet<>();
        for (String pageId : cfg.getPageIds()) {
            try {
                allVideoIds.addAll(discoverVideoIdsForPage(pageId));
            } catch (Exception e) {
                log.warn("[FB] page {}: {}", pageId, e.toString());
            }
        }
        if (allVideoIds.isEmpty()) return 0;

        return collectAndPushByIds(new ArrayList<>(allVideoIds))
                .blockOptional().orElse(0);
    }

    /** Collecte par IDs, prépare sources + metrics, pousse côté API de manière non bloquante. */
    public Mono<Integer> collectAndPushByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Mono.just(0);

        final String v = cfg.getApiVersion();
        final String token = cfg.getAccessToken();

        return Flux.fromIterable(ids)
                .flatMap(id ->
                                Mono.zip(
                                                fb.video(v, id, token),
                                                fb.insights(v, id, token)
                                        )
                                        .map(tuple -> {
                                            VideoResponse video = tuple.getT1();
                                            InsightsResponse insResp = tuple.getT2();

                                            SourceUpsertItem src = FacebookMetricsMapper.toSource(video);
                                            Map<String, Long> metricsMap = toFlatMap(insResp);
                                            MetricsUpsertItem met = FacebookMetricsMapper.fromVideoAndInsights(video, metricsMap);
                                            return new SrcAndMet(src, met);
                                        })
                                        .onErrorResume(ex -> {
                                            log.warn("[FB] skip id={} cause={}", id, ex.toString());
                                            return Mono.empty();
                                        })
                        , /*concurrency*/ 6)
                .collectList()
                .flatMap(list -> Mono.fromRunnable(() -> {
                                    var sources  = list.stream().map(SrcAndMet::src).toList();
                                    var snapshots= list.stream().map(SrcAndMet::met).toList();
                                    log.info("[FB] upsert {} sources, {} metrics", sources.size(), snapshots.size());

                                    // ⚠ appels bloquants → boundedElastic
                                    sink.batchUpsertSources(sources);
                                    sink.batchUpsertMetrics(snapshots);
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                )
                .thenReturn(ids.size());
    }

    /** Découverte d’IDs vidéo via /{page}/published_posts + attachments{media_type,target}. */
    private List<String> discoverVideoIdsForPage(String pageId) {
        List<String> out = new ArrayList<>();
        String after = null;
        int pages = 0;

        // Fenêtre de collecte (rolling)
        int days = cfg.getWindowDaysRolling();
        String since = LocalDate.now(ZoneOffset.UTC).minusDays(days).atStartOfDay().toInstant(ZoneOffset.UTC).toString();
        String until = Instant.now().toString();

        do {
            FacebookPostsResponse resp = fb.publishedPosts(
                    cfg.getApiVersion(), pageId, cfg.getAccessToken(),
                    cfg.getPageSize(), after, since, until
            ).block(); // Mono → BLOQUANT ponctuel ici OK si rare, sinon transformer en chaîne réactive complète

            if (resp == null || resp.getData() == null) break;
            for (var post : resp.getData()) {
                if (post.getAttachments() == null || post.getAttachments().getData() == null) continue;
                for (var att : post.getAttachments().getData()) {
                    var mediaType = att.getMediaType();
                    var target = att.getTarget();
                    if (target == null || StringUtils.isBlank(target.getId())) continue;

                    if (StringUtils.containsIgnoreCase(mediaType, "video")) {
                        out.add(target.getId());
                    }
                }
            }

            if (resp.getPaging() == null || resp.getPaging().getCursors() == null) break;
            String nextAfter = resp.getPaging().getCursors().getAfter();
            after = StringUtils.isNotBlank(nextAfter) ? nextAfter : null;
            pages++;
        } while (after != null && out.size() < cfg.getMaxVideosPerRun());

        log.info("[FB] page {} -> {} videoIds (pages={})", pageId, out.size(), pages);
        return out.stream().distinct().limit(cfg.getMaxVideosPerRun()).collect(Collectors.toList());
    }

    /** Aplatisseur simple: InsightsResponse → Map<name, firstNumericValue>. */
    static Map<String, Long> toFlatMap(InsightsResponse resp) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (resp == null || resp.data() == null) return out;

        for (InsightMetric m : resp.data()) {
            if (m.values() == null || m.values().isEmpty()) continue;

            JsonNode jn = m.values().get(0).value();
            if (jn == null) continue;

            if (jn.isNumber()) {
                // Numérique simple → on garde tel quel (Long)
                Long v = coerceToLong(jn);
                if (v != null) out.put(m.name(), v);
                continue;
            }

            if (jn.isObject()) {
                // Breakdown : ex {"like":61,"love":7} ou {"0":0.9811,"1":0.1046,...}
                jn.fields().forEachRemaining(e -> {
                    String subKey = normalizeKey(e.getKey());
                    Long v = coerceToLong(e.getValue());
                    if (v != null) out.put(m.name() + "_" + subKey, v);
                });
                continue;
            }

            if (jn.isArray()) {
                // Rare mais possible : un array de numbers/objets
                for (int i = 0; i < jn.size(); i++) {
                    JsonNode el = jn.get(i);
                    String idxKey = Integer.toString(i);

                    if (el.isNumber()) {
                        Long v = coerceToLong(el);
                        if (v != null) out.put(m.name() + "_" + idxKey, v);
                    } else if (el.isObject()) {
                        el.fields().forEachRemaining(e -> {
                            String subKey = idxKey + "_" + normalizeKey(e.getKey());
                            Long v = coerceToLong(e.getValue());
                            if (v != null) out.put(m.name() + "_" + subKey, v);
                        });
                    } // (autres types ignorés)
                }
            }
            // autres types (boolean, text, null) ignorés
        }
        return out;
    }

// --- Helpers ---

    private static final int DECIMAL_SCALE = 10_000;

    /** Convertit un JsonNode numérique (ou texte numérique) en Long.
     *  - entiers: tel quel
     *  - décimaux: arrondi(x * DECIMAL_SCALE)
     */
    private static Long coerceToLong(JsonNode n) {
        try {
            if (n == null || n.isNull()) return null;

            if (n.isIntegralNumber()) {
                return n.longValue();
            }
            if (n.isFloatingPointNumber()) {
                // ex: 0.9811 → 9811 (4 décimales)
                double d = n.doubleValue();
                return Math.round(d * DECIMAL_SCALE);
            }
            if (n.isTextual()) {
                String s = n.asText().trim();
                if (s.isEmpty()) return null;
                // tente entier, sinon décimal
                if (s.matches("^-?\\d+$")) {
                    return Long.parseLong(s);
                }
                if (s.matches("^-?\\d*\\.\\d+$")) {
                    double d = Double.parseDouble(s);
                    return Math.round(d * DECIMAL_SCALE);
                }
            }
        } catch (Exception ignore) {
            // on ignore silencieusement les valeurs non convertibles
        }
        return null;
    }

    /** Autorise chiffres/lettres/underscore uniquement. */
    private static String normalizeKey(String k) {
        if (k == null) return "";
        return k.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private record SrcAndMet(SourceUpsertItem src, MetricsUpsertItem met) {}
}
