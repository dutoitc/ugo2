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

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacebookCollectorService {

    private final FacebookClient fb;
    private final FacebookProps cfg;
    private final WebApiSinkService sink;

    /**
     * Collecte multi-pages + upsert sources + upsert metrics.
     */
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

    /**
     * Collecte par IDs, prépare sources + metrics, pousse côté API de manière non bloquante.
     */
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
                                    var sources = list.stream().map(SrcAndMet::src).toList();
                                    var snapshots = list.stream().map(SrcAndMet::met).toList();
                                    log.info("[FB] upsert {} sources, {} metrics", sources.size(), snapshots.size());

                                    // ⚠ appels bloquants → boundedElastic
                                    sink.batchUpsertSources(sources);
                                    sink.batchUpsertMetrics(snapshots);
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                )
                .thenReturn(ids.size());
    }

    /**
     * Découverte d’IDs vidéo via:
     * - /published_posts   → vidéo visibles dans la timeline
     * - /videos?type=uploaded → toutes les vidéos natives (même anciennes)
     */
    private List<String> discoverVideoIdsForPage(String pageId) {
        Set<String> out = new LinkedHashSet<>();
        final String version = cfg.getApiVersion();
        final String token = cfg.getAccessToken();

        out.addAll(findVideos(pageId, version, token, cfg.getPageSize(), null));
        out.addAll(findUploaded(pageId, version, token, out));
        out.addAll(findReels(pageId, version, token, out));

        return out.stream()
                .distinct()
                .limit(cfg.getMaxVideosPerRun())
                .toList();
    }

    /** /published_posts (pagination) */
    private Collection<String> findVideos(String pageId, String version, String token, int limit, String after) {
        int pages = 0;

        Set<String> out1 = new LinkedHashSet<>();
        do {
            FacebookPostsResponse resp =
                    fb.publishedPosts(version, pageId, token, limit, after)
                            .block();

            if (resp == null || resp.getData() == null) break;

            for (var post : resp.getData()) {
                if (post.getAttachments() == null || post.getAttachments().getData() == null)
                    continue;

                for (var att : post.getAttachments().getData()) {
                    out1.addAll(getVideoIds(att));
                }
            }

            pages++;
            after = (resp.getPaging() != null &&
                    resp.getPaging().getCursors() != null &&
                    StringUtils.isNotBlank(resp.getPaging().getCursors().getAfter()))
                    ? resp.getPaging().getCursors().getAfter()
                    : null;

        } while (after != null && out1.size() < cfg.getMaxVideosPerRun());

        log.info("[FB] page {} -> {} videoIds (pages={})", pageId, out1.size(), pages);
        return out1;
    }

    private static Collection<String> getVideoIds(FacebookPostsResponse.Attachment att) {
        // media_type = video
        if (StringUtils.containsIgnoreCase(att.getMediaType(), "video")
                && att.getTarget() != null) {
            return List.of(att.getTarget().getId());
        }

        // target.type = video
        if (att.getTarget() != null &&
                "video".equalsIgnoreCase(att.getTarget().getType())) {
            return List.of(att.getTarget().getId());
        }

        // subattachments
        if (att.getSubattachments() != null && att.getSubattachments().getData() != null) {
            var lst = new ArrayList<String>();
            for (var sub : att.getSubattachments().getData()) {
                if (sub.getTarget() != null &&
                        "video".equalsIgnoreCase(sub.getTarget().getType())) {
                    lst.add(sub.getTarget().getId());
                }
            }
            return lst;
        }

        // media.id (reels / inline videos)
        if (att.getMedia() != null && att.getMedia().getId() != null) {
            return List.of(att.getMedia().getId());
        }

        return List.of();
    }


    /** videos?type=uploaded */
    private List<String> findUploaded(String pageId, String version, String token, Set<String> out) {
        try {
            List<String> uploaded = fb.videosUploaded(version, pageId, token)
                    .blockOptional()
                    .orElse(List.of());
            log.info("FindUploaded returned {}", uploaded.size());
            return uploaded;
        } catch (Exception e) {
            log.warn("[FB] page {} /videos?type=uploaded failed: {}", pageId, e.toString());
        }
        return List.of();
    }

    /** videos?type=reels */
    private List<String> findReels(String pageId, String version, String token, Set<String> out) {
        try {
            List<String> reels = fb.videosReels(version, pageId, token)
                    .blockOptional()
                    .orElse(List.of());
            log.info("FindReels returned {}", reels.size());
            return reels;
        } catch (Exception e) {
            log.warn("[FB] page {} /videos?type=reels failed: {}", pageId, e.toString());
        }
        return List.of();
    }


    /**
     * Aplatisseur simple: InsightsResponse → Map<name, firstNumericValue>.
     */
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

    /**
     * Convertit un JsonNode numérique (ou texte numérique) en Long.
     * - entiers: tel quel
     * - décimaux: arrondi(x * DECIMAL_SCALE)
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

    /**
     * Autorise chiffres/lettres/underscore uniquement.
     */
    private static String normalizeKey(String k) {
        if (k == null) return "";
        return k.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private record SrcAndMet(SourceUpsertItem src, MetricsUpsertItem met) {
    }
}
