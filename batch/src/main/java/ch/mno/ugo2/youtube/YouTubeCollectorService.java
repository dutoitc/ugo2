package ch.mno.ugo2.youtube;

import ch.mno.ugo2.config.YouTubeProps;
import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.service.WebApiSinkService;
import ch.mno.ugo2.youtube.responses.ChannelsContentDetailsResponse;
import ch.mno.ugo2.youtube.responses.PlaylistItemsResponse;
import ch.mno.ugo2.youtube.responses.VideoListResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ch.mno.ugo2.dto.SourceUpsertItem;
import reactor.core.scheduler.Schedulers;


import java.util.*;

/**
 * Collecte l'historique des vidéos de chaînes configurées puis pousse les métriques vers l'API.
 * - Découverte: channels.list -> relatedPlaylists.uploads -> playlistItems (pagination)
 * - Poussée: /api/v1/metrics:batchUpsert
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeCollectorService {

    private final YouTubeClient yt;
    private final YouTubeProps cfg;
    private final WebApiSinkService sink;

    /**
     * Collecte complète (toutes les chaînes en config) et retourne le nombre de snapshots poussés.
     */
    public int collect() {
        String apiKey = cfg.getApiKey();
        List<String> channelIds = cfg.getChannelIds();

        if (channelIds.isEmpty()) {
            log.warn("[YT] aucune chaîne configurée (youtube.channelIds vide)");
            return 0;
        }

        Set<String> allVideoIds = new LinkedHashSet<>();
        for (String channelId : channelIds) {
            try {
                String uploadsPlaylist = findUploadsPlaylist(apiKey, channelId);
                if (uploadsPlaylist == null) continue;
                allVideoIds.addAll(listPlaylistVideoIds(apiKey, uploadsPlaylist));
            } catch (Exception e) {
                log.warn("[YT] channel {}: {}", channelId, e.toString());
            }
        }

        if (allVideoIds.isEmpty()) return 0;
        return collectAndPushByIds(new ArrayList<>(allVideoIds)).blockOptional().orElse(0);
    }

    /**
     * Collecte/pousse un lot d’IDs connus (utilitaire, utilisé par collect()).
     */
    public Mono<Integer> collectAndPushByIds(List<String> videoIds) {
        log.info("collectAndPushByIds for {} videos", (videoIds == null ? "null" : videoIds.size()));
        if (videoIds == null || videoIds.isEmpty()) return Mono.just(0);

        String apiKey = cfg.getApiKey();
        List<List<String>> chunks = chunk(videoIds, 50);

        return Flux.fromIterable(chunks)
                .flatMap(chunk -> yt.videosList(apiKey, chunk))
                .flatMap(resp -> Flux.fromIterable(
                        Optional.ofNullable(resp.getItems()).orElseGet(List::of)))
                .collectList()
                .flatMap(items -> {
                    if (items.isEmpty()) return Mono.just(0);

                    // 1) build SOURCES
                    List<SourceUpsertItem> sources = new ArrayList<>(items.size());
                    // 2) build METRICS
                    List<MetricsUpsertItem> snapshots = new ArrayList<>(items.size());

                    for (VideoListResponse.Item it : items) {
                        var s  = it.getSnippet();
                        var cd = it.getContentDetails();
                        sources.add(SourceUpsertItem.builder()
                                .platform("YOUTUBE")
                                .platform_source_id(it.getId())                    // clé de source = id vidéo
                                .title(s.getTitle())
                                .description(s.getDescription())
                                .permalink_url("https://www.youtube.com/watch?v=" + it.getId())
                                .media_type(cd.getDuration().toSeconds() <= 60 ? "SHORT" : "VIDEO")
                                .duration_seconds((int)cd.getDuration().toSeconds())
                                .published_at(s.getPublishedAt().toString())                  // déjà ISO-8601
                                .is_teaser(0)
                                .video_id(null)
                                .locked(0)
                                .build());

                        snapshots.add(YouTubeMetricsMapper.fromVideoResource(it));
                    }

                    log.info("[YT] mapped {} sources & {} snapshots", sources.size(), snapshots.size());

                    // ⚠️ Appels BLOQUANTS déplacés sur boundedElastic
                    return Mono.fromRunnable(() -> {
                                sink.batchUpsertSources(sources);   // appelle bloquant interne (block())
                                sink.batchUpsertMetrics(snapshots); // idem
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .thenReturn(items.size());
                });
    }

    /* ------------------- internals ------------------- */

    private String findUploadsPlaylist(String apiKey, String channelId) {
        log.info("findUploadsPlaylist, channelId={}", channelId);
        ChannelsContentDetailsResponse response = yt.channelsContentDetails(apiKey, channelId).block();
        if (response == null) return null;
        var items = response.getItems();
        if (items == null || items.isEmpty()) return null;
        return items.getFirst()
                .getContentDetails()
                .getRelatedPlaylists()
                .getUploads();
    }

    private List<String> listPlaylistVideoIds(String apiKey, String playlistId) {
        log.info("listPlaylistVideoIds, playlistId={}", playlistId);
        List<String> out = new ArrayList<>();
        String pageToken = null;
        do {
            var page = yt.playlistItems(apiKey, playlistId, 50, pageToken, null).block();
            if (page == null) break;
            var items = page.getItems();
            if (items != null) {
                for (PlaylistItemsResponse.Item it : items) {
                    String vid = it.getContentDetails().getVideoId();
                    if (vid != null && !vid.isBlank()) out.add(vid);
                }
            }
            String next = page.getNextPageToken();
            pageToken = (next != null && !next.isBlank()) ? next : null;
        } while (pageToken != null);
        log.info("[YT] playlist {} -> {} videoIds", playlistId, out.size());
        return out;
    }

    private static <T> List<List<T>> chunk(List<T> in, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < in.size(); i += size) out.add(in.subList(i, Math.min(i + size, in.size())));
        return out;
    }
}
