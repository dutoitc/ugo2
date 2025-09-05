package ch.mno.ugo2.facebook;

import ch.mno.ugo2.config.FacebookProps;
import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.facebook.responses.FacebookPostsResponse;
import ch.mno.ugo2.facebook.responses.InsightValue;
import ch.mno.ugo2.facebook.responses.InsightsResponse;
import ch.mno.ugo2.facebook.responses.VideoResponse;
import ch.mno.ugo2.service.WebApiSinkService;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Découvre les posts publiés pour chaque page configurée, extrait les IDs vidéo/reel,
 * récupère métriques (video + insights) et pousse vers l'API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacebookCollectorService {

    private final ch.mno.ugo2.facebook.FacebookClient fb;
    private final FacebookProps cfg;
    private final WebApiSinkService sink;


    /**
     * @return nombre de snapshots poussés
     */
    public int collect() {
        List<String> pageIds = cfg.getPageIds(); // ex: app.yaml → facebook.pageIds: [123..., 456...]
        if (pageIds == null || pageIds.isEmpty()) {
            log.warn("[FB] aucune page configurée (facebook.pageIds vide)");
            return 0;
        }

        Set<String> allVideoIds = new LinkedHashSet<>();
        for (String pageId : pageIds) {
            try {
                allVideoIds.addAll(discoverVideoIdsForPage(pageId));
            } catch (Exception e) {
                log.warn("[FB] page {}: {}", pageId, e.toString());
            }
        }

        if (allVideoIds.isEmpty()) return 0;
        return collectAndPushByIds(new ArrayList<>(allVideoIds)).blockOptional().orElse(0);
    }

    /** Collecte/pousse un lot d’IDs connus (utilitaire, utilisé par collect()). */
    public Mono<Integer> collectAndPushByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Mono.just(0);

        return Flux.fromIterable(ids)
                .flatMap(this::fetchOne)           // Mono<MetricsUpsertItem>
                .collectList()
                .doOnNext(list -> {
                    log.info("[FB] mapped {} snapshots", list.size());
                    sink.batchUpsertMetrics(list);
                })
                .map(List::size);
    }

    /* ------------------- internals ------------------- */

    private List<String> discoverVideoIdsForPage(String pageId) {
        List<String> out = new ArrayList<>();
        String after = null;
        int pages = 0;

        do {
            var q = FacebookQuery.buildQueryPublishedPosts(cfg, pageId, after);
            var resp = fb.get(q, FacebookPostsResponse.class).block();
            if (resp == null) break;

            var data = resp.getData();
            if (data != null) {
                for (var post : data) {
                    // attachments{media_type,target{id}}
                    var attachments = post.getAttachments().getData();
                    for (var att : attachments) {
                        String mediaType = att.getMediaType();
                        if (att.getTarget()==null) continue;
                        String targetId  = att.getTarget().getId();
                        if (StringUtils.isBlank(targetId)) continue;

                        // on garde les objets vidéo/reel uniquement
                        if (mediaType.toLowerCase(Locale.ROOT).contains("video")) {
                            out.add(targetId); // videoId (utilisable pour /{id} et /{id}/video_insights)
                        }
                    }
                }
            }

            // pagination
            if (resp.getPaging()==null) break;
            String nextAfter = resp.getPaging().getCursors().getAfter();
            after = StringUtils.isNotBlank(nextAfter)? nextAfter : null;

            pages++;
        } while (after != null);

        log.info("[FB] page {} -> {} videoIds (pages={}", pageId, out.size(), pages);
        return out;
    }

    private Mono<MetricsUpsertItem> fetchOne(String id) {
        // 1) Détails vidéo/reel (ajout product_type,is_reel pour détecter REEL vs VIDEO)
        FacebookQuery videoQ = FacebookQuery.builder()
                .version(cfg.getApiVersion())
                .video(id)
                .fields("id,permalink_url,created_time,length") // exists: length,post_views,likes,title,description,views,published,scheduled_publish_time
                .accessToken(cfg.getAccessToken())
                .build();

        // 2) Insights (superset)
        FacebookQuery insightsQ = FacebookQuery.buildQueryInsights(cfg, id, null); // null = all metrics

        Mono<VideoResponse> videoMono    = fb.get(videoQ, VideoResponse.class);
        Mono<InsightsResponse> insightsMono = fb.get(insightsQ, InsightsResponse.class);

        return Mono.zip(videoMono, insightsMono)
                .map(t -> {
                    VideoResponse video = t.getT1();
                    Map<String, Long> insights = toInsightMap(t.getT2());
                    return FacebookMetricsMapper.fromVideoAndInsights(video, insights);
                })
                .onErrorResume(e -> {
                    log.warn("[FB] {} -> {}", id, e.toString());
                    return Mono.empty();
                });
    }

    private Map<String, Long> toInsightMap(InsightsResponse insightsResp) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (insightsResp == null) return out;

        var data = insightsResp.data();
        if (data == null) return out;

        for (var metric : data) {
            String name = metric.name();
            if (name == null) continue;

            Long v = extractFirstValue(metric.values());
            if (v != null) out.put(name, v);
        }
        return out;
    }

    private Long extractFirstValue(List<InsightValue> valuesNode) {
        if (valuesNode == null || valuesNode.isEmpty()) return null;
        JsonNode val = valuesNode.getFirst().value();
        if (val == null) return null;

        if (val.isNumber()) return val.longValue();
        if (val.isTextual()) {
            try { return Long.parseLong(val.asText()); } catch (NumberFormatException ignored) {}
        }
        return null; // parfois FB renvoie un objet → on ignore
    }
}
