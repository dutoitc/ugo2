package ch.mno.ugo2.youtube;

import ch.mno.ugo2.api.WebApiClient;
import ch.mno.ugo2.config.YouTubeProps;
import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import ch.mno.ugo2.service.WebApiSinkService;
import ch.mno.ugo2.util.IsoDurations;
import ch.mno.ugo2.util.JsonStateStore;
import ch.mno.ugo2.util.TeaserHeuristics;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeCollectorService {
    private final YouTubeProps props;
    private final YouTubeClient yt;
    private final WebApiSinkService sink;
    private final WebApiClient api;

    public int collect(boolean initial) {
        var chs = props.getChannelIds();
        log.debug("YT collect initial={}, channels={}", initial, chs);
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            log.warn("YouTube API key missing — skipping");
            return 0;
        }
        if (chs == null || chs.isEmpty()) {
            log.warn("YouTube channelIds empty — nothing to do");
            return 0;
        }

        int totalMetrics = 0;
        int totalSources = 0;
        for (String channel : chs) {
            var res = collectChannel(channel.trim(), initial);
            totalMetrics += res.metrics;
            totalSources += res.sources;
        }
        log.info("YouTube: totalSources={}, totalMetricsSent={}", totalSources, totalMetrics);
        return totalMetrics;
    }

    private record Collected(int sources, int metrics) {
    }

    private Collected collectChannel(String channelId, boolean initial) {
        int pushedSources = 0;
        int pushedMetrics = 0;
        try {
            Path statePath = Path.of(props.getStateDir(), "youtube-" + channelId + ".json");
            JsonStateStore state = new JsonStateStore(statePath);
            LocalDateTime cutoff = initial ? LocalDateTime.of(1970, 1, 1, 0, 0) :
                    LocalDate.now(ZoneOffset.UTC).minusDays(props.getWindowDaysRolling()).atStartOfDay();
            log.debug("YT channel={}, initial={}, cutoff={}", channelId, initial, cutoff);

            String uploads = fetchUploadsPlaylistId(channelId);
            log.debug("YT uploads playlistId={}", uploads);
            if (uploads == null) {
                log.warn("No uploads playlist for {}", channelId);
                return new Collected(0, 0);
            }

            List<String> videoIds = new ArrayList<>();
            String pageToken = null;
            int pages = 0;
            boolean stopByCutoff = false;

            while (true) {
                String etagKey = "pl:" + uploads + ":" + (pageToken == null ? "" : pageToken);
                // IMPORTANT : pas d'ETag en mode init (sinon 304 et 0 résultat)
                String etag = initial ? null : state.getEtag(etagKey);
                log.debug("YT page request uploads={}, token={}, etag={}", uploads, pageToken, etag);

                JsonNode page = yt.playlistItems(props.getApiKey(), uploads, props.getPageSize(), pageToken, etag).block();
                if (page == null) { // 304
                    log.debug("YT page {} -> 304 Not Modified", etagKey);
                    break;
                }
                pages++;

                String newEtag = page.path("etag").asText(null);
                if (!initial && newEtag != null) {
                    state.setEtag(etagKey, newEtag);
                }

                int before = videoIds.size();
                for (JsonNode item : page.path("items")) {
                    JsonNode cd = item.path("contentDetails");
                    String vid = cd.path("videoId").asText(null);
                    String published = item.path("snippet").path("publishedAt").asText(null);
                    LocalDateTime pAt = parseInstant(published);

                    if (!initial && pAt != null && pAt.isBefore(cutoff)) {
                        stopByCutoff = true;
                        break; // stop lire items de cette page, et on sortra du while après
                    }
                    if (vid != null) videoIds.add(vid);
                    if (props.getMaxVideosPerRun() > 0 && videoIds.size() >= props.getMaxVideosPerRun()) {
                        stopByCutoff = true;
                        break;
                    }
                }
                int added = videoIds.size() - before;
                log.debug("YT page {}: items+={} (total={})", pages, added, videoIds.size());

                if (stopByCutoff || (props.getMaxVideosPerRun() > 0 && videoIds.size() >= props.getMaxVideosPerRun()))
                    break;
                pageToken = page.path("nextPageToken").asText(null);
                if (pageToken == null) break;
            }

            log.info("YouTube scanning: pages={}, collectedIds={}", pages, videoIds.size());
            if (videoIds.isEmpty()) {
                state.save();
                return new Collected(0, 0);
            }

            var res = enrichAndPush(state, videoIds, initial);
            state.save();
            log.info("YouTube channel {}: pushedSources={}, pushedMetrics={}", channelId, res.sources, res.metrics);
            return res;
        } catch (Exception e) {
            log.warn("collectChannel failed for {}: {}", channelId, e.toString());
            return new Collected(0, 0);
        }
    }

    private Collected enrichAndPush(JsonStateStore state, List<String> videoIds, boolean initial) {
        int sourcesCount = 0;
        int metricsCount = 0;
        final int CHUNK = 50;
        for (int i = 0; i < videoIds.size(); i += CHUNK) {
            List<String> slice = videoIds.subList(i, Math.min(i + CHUNK, videoIds.size()));
            JsonNode v = yt.videosList(props.getApiKey(), slice).block();
            if (v == null) continue;

            // 1) IDs de ce slice
            List<String> idsSlice = new ArrayList<>();
            for (JsonNode it : v.path("items")) {
                String id = it.path("id").asText(null);
                if (id != null) idsSlice.add(id);
            }
            // 2) Quels manquent côté API ?
            List<String> missing = api.filterMissingSources("YOUTUBE", idsSlice).block();
            Set<String> missingSet = new HashSet<>(missing == null ? List.of() : missing);
            log.debug("YT slice: ids={}, missing={}", idsSlice.size(), missingSet.size());

            List<SourceUpsertItem> sources = new ArrayList<>();
            List<MetricsUpsertItem> metrics = new ArrayList<>();

            for (JsonNode it : v.path("items")) {
                String id = it.path("id").asText();
                JsonNode sn = it.path("snippet");
                JsonNode st = it.path("statistics");
                JsonNode cd = it.path("contentDetails");
                String title = sn.path("title").asText(null);
                String descr = sn.path("description").asText(null);
                LocalDateTime publishedAt = parseInstant(sn.path("publishedAt").asText(null));
                Integer durationSec = IsoDurations.toSeconds(cd.path("duration").asText(null));
                boolean isShort = durationSec != null && durationSec <= 61;
                boolean teaser = TeaserHeuristics.isTeaser(title, descr, durationSec);

                // Nouvelle source ?
                if (missingSet.contains(id)) {
                    sources.add(SourceUpsertItem.builder()
                            .platform("YOUTUBE")
                            .platform_source_id(id)
                            .title(title)
                            .description(descr)
                            .permalink_url("https://www.youtube.com/watch?v=" + id)
                            .media_type(isShort ? "SHORT" : "VIDEO")
                            .duration_seconds(durationSec)
                            .published_at(publishedAt == null ? null : publishedAt.toString())
                            .is_teaser(teaser ? 1 : 0)
                            .video_id(null)
                            .locked(0)
                            .build());

                    // Baseline à published_at (0 vues) si date connue
                    if (publishedAt != null) {
                        metrics.add(MetricsUpsertItem.builder()
                                .platform("YOUTUBE")
                                .platform_source_id(id)
                                .captured_at(publishedAt.toString())
                                .views_3s(0)
                                .views_platform_raw(0)
                                .comments(0)
                                .shares(null)
                                .reactions(0)
                                .saves(null)
                                .build());
                    }
                }

                // Snapshot courant (delta/plancher)
                Integer viewsRaw = st.hasNonNull("viewCount") ? st.get("viewCount").asInt() : null;
                Integer likes = st.hasNonNull("likeCount") ? st.get("likeCount").asInt() : null;
                Integer comments = st.hasNonNull("commentCount") ? st.get("commentCount").asInt() : null;

                if (shouldSend(state, id, viewsRaw)) {
                    metrics.add(MetricsUpsertItem.builder()
                            .platform("YOUTUBE")
                            .platform_source_id(id)
                            .captured_at(nowUtcIso())
                            .views_3s(viewsRaw)
                            .views_platform_raw(viewsRaw)
                            .comments(comments)
                            .shares(null)
                            .reactions(likes)
                            .saves(null)
                            .build());
                    var vs = state.getVideoState(id);
                    vs.put("lastSentViews", viewsRaw == null ? 0 : viewsRaw);
                    vs.put("lastSentAt", LocalDate.now(ZoneOffset.UTC).toString());
                }
            }

            if (!sources.isEmpty()) {
                sink.pushSources(sources);
                sourcesCount += sources.size();
                log.info("Pushed YT sources chunk: {}", sources.size());
            } else {
                log.debug("No new YT sources to push in this chunk");
            }
            if (!metrics.isEmpty()) {
                sink.pushMetrics(metrics);
                metricsCount += metrics.size();
                log.info("Pushed YT metrics chunk: {}", metrics.size());
            } else {
                log.debug("No YT metrics to push in this chunk");
            }
        }
        return new Collected(sourcesCount, metricsCount);
    }

    private boolean shouldSend(JsonStateStore state, String videoId, Integer viewsRaw) {
        var vs = state.getVideoState(videoId);
        Integer last = vs.get("lastSentViews") == null ? null : ((Number) vs.get("lastSentViews")).intValue();
        String lastAtS = (String) vs.get("lastSentAt");
        LocalDate lastAt = lastAtS == null ? null : LocalDate.parse(lastAtS);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        if (last == null) return true; // première fois
        if (viewsRaw == null) return false;
        int abs = viewsRaw - last;
        double rel = last == 0 ? 1.0 : (viewsRaw - last) / (double) last;
        if (abs >= props.getMinDeltaAbs() || rel >= props.getMinDeltaRel()) return true;
        if (props.isDailyFloor() && (lastAt == null || !lastAt.equals(today))) return true;
        return false;
    }

    private String fetchUploadsPlaylistId(String channelId) {
        JsonNode n = yt.channelsContentDetails(props.getApiKey(), channelId).block();
        if (n == null) return null;
        var items = n.path("items");
        if (!items.isArray() || items.isEmpty()) return null;
        var cd = items.get(0).path("contentDetails");
        var rp = cd.path("relatedPlaylists");
        return rp.path("uploads").asText(null);
    }

    private static LocalDateTime parseInstant(String iso) {
        if (iso == null) return null;
        try {
            return LocalDateTime.ofInstant(Instant.parse(iso), ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * ISO-8601 UTC (Z) pour "maintenant", sans millisecondes.
     */
    private static String nowUtcIso() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /**
     * Convertit un LocalDateTime (UTC) en ISO-8601 UTC (Z) sans millisecondes.
     */
    private static String toUtcIso(LocalDateTime ldtUtc) {
        return OffsetDateTime.of(ldtUtc, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString();
    }

}