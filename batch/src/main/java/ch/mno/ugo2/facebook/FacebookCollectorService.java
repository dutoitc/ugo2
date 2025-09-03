package ch.mno.ugo2.facebook;

import ch.mno.ugo2.api.FacebookClient;
import ch.mno.ugo2.config.FacebookProps;
import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import ch.mno.ugo2.service.WebApiSinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacebookCollectorService {

  private final FacebookClient fb;
  private final FacebookProps cfg;
  private final WebApiSinkService sink;

  /**
   * Charge toutes les publications vidéo (vidéos natives + reels) d’une Page.
   * - upsert des sources
   * - baseline 0 @ published_at (si connu)
   * - snapshot courant @ now UTC
   *      * vidéo classique: total_video_views
   *      * REELS fallback: total_video_plays puis total_video_plays_unique
   */
  public int collect() {
    Set<String> seenVideoIds = new HashSet<>();
    List<SourceUpsertItem> sources = new ArrayList<>();
    List<MetricsUpsertItem> metrics = new ArrayList<>();
    int pushed = 0;

    for (String pageId : cfg.getPageIds()) {
      log.info("FB: scanning page {} (ALL history)", pageId);

      String postFields = String.join(",",
              "id",
              "created_time",
              "permalink_url",
              "attachments{media_type,target{id}}"
      );
      List<Map<String, Object>> posts = fb.listPublishedPosts(pageId, postFields, cfg.getPageSize(), cfg.getAccessToken());
      if (posts == null) continue;

      for (Map<String, Object> post : posts) {
        String postId = str(post.get("id"));
        String videoId = extractVideoId(post);
        if (videoId == null) continue;
        if (!seenVideoIds.add(videoId)) continue;

        Map<String, Object> video = fb.get("/" + videoId, cfg.getAccessToken(),
                "id,length,description,title,created_time,permalink_url");
        if (video == null) continue;

        String title = str(video.get("title"));
        String desc  = str(video.get("description"));
        String vPermalink = str(video.get("permalink_url"));
        String postPermalink = str(post.get("permalink_url"));
        Integer durationSec = asInt(video.get("length"));
        Instant pub = parseIso(str(video.get("created_time")));

        boolean isTeaser = false; // garde ta logique teaser si besoin

        // Upsert source
        sources.add(SourceUpsertItem.builder()
                .platform("FACEBOOK")
                .platform_source_id(videoId)
                .title(title)
                .description(desc)
                .permalink_url(notBlank(vPermalink) ? vPermalink : postPermalink)
                .media_type("VIDEO")
                .duration_seconds(durationSec)
                .published_at(toUtcIsoInstant(pub))
                .is_teaser(isTeaser ? 1 : 0)
                .locked(0)
                .build());

        // Baseline @published_at
        if (pub != null) {
          metrics.add(MetricsUpsertItem.builder()
                  .platform("FACEBOOK")
                  .platform_source_id(videoId)
                  .captured_at(OffsetDateTime.ofInstant(pub, ZoneOffset.UTC)
                          .truncatedTo(ChronoUnit.SECONDS).toString())
                  .views_3s(0)
                  .views_platform_raw(0)
                  .comments(0)
                  .shares(0)
                  .reactions(0)
                  .saves(0)
                  .build());
        }

        // Snapshot courant @now — standard + REELS fallbacks
        Integer views = fetchTotalVideoViews(videoId);                 // vidéo « classique »
        if (views == null) {
          // REELS: plays (total)
          views = fetchTotalVideoPlays(videoId);                      // total_video_plays
          if (views == null) {
            // REELS: uniques (fallback ultime)
            views = fetchTotalVideoPlaysUnique(videoId);              // total_video_plays_unique
          }
        }

        MetricsUpsertItem.MetricsUpsertItemBuilder mb = MetricsUpsertItem.builder()
                .platform("FACEBOOK")
                .platform_source_id(videoId)
                .captured_at(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString());

        if (views != null) {
          mb.views_3s(views).views_platform_raw(views);
        } else {
          mb.views_3s(0).views_platform_raw(0);
        }
        mb.comments(0).shares(0).reactions(0).saves(0);
        metrics.add(mb.build());

        // Flush périodique
        if (sources.size() >= 100) { sink.pushSources(sources); sources.clear(); }
        if (metrics.size() >= 200)  { sink.pushMetrics(metrics); metrics.clear(); }
      }
    }

    if (!sources.isEmpty()) sink.pushSources(sources);
    if (!metrics.isEmpty()) sink.pushMetrics(metrics);

    pushed += metrics.size();
    log.info("Facebook pushed snapshots={}", pushed);
    return pushed;
  }

  // === Metrics helpers ======================================================

  /** Vidéos classiques: /video_insights/total_video_views/lifetime */
  Integer fetchTotalVideoViews(String videoId) {
    return extractLastValue(
            fb.get("/" + videoId + "/video_insights/total_video_views/lifetime",
                    cfg.getAccessToken(), null));
  }

  /** REELS (1) : /video_insights/total_video_plays/lifetime */
  Integer fetchTotalVideoPlays(String videoId) {
    // REELS: plays = nombre total de lectures (Meta doc)
    return extractLastValue(
            fb.get("/" + videoId + "/video_insights/total_video_plays/lifetime",
                    cfg.getAccessToken(), null));
  }

  /** REELS (2) : /video_insights/total_video_plays_unique/lifetime */
  Integer fetchTotalVideoPlaysUnique(String videoId) {
    // REELS uniques: nombre de spectateurs uniques (Meta doc)
    return extractLastValue(
            fb.get("/" + videoId + "/video_insights/total_video_plays_unique/lifetime",
                    cfg.getAccessToken(), null));
  }

  @SuppressWarnings("unchecked")
  private Integer extractLastValue(Map<String, Object> resp) {
    if (resp == null) return null;
    Object data = resp.get("data");
    if (data instanceof List<?> list && !list.isEmpty()) {
      Object first = list.get(0);
      if (first instanceof Map<?, ?> m) {
        Object values = m.get("values");
        if (values instanceof List<?> vl && !vl.isEmpty()) {
          Object last = vl.get(vl.size() - 1);
          if (last instanceof Map<?, ?> lv) {
            Object v = lv.get("value");
            return asInt(v);
          }
        }
      }
    }
    return null;
  }

  // === Post parsing / utils =================================================

  private static String extractVideoId(Map<String, Object> post) {
    Object attachments = post.get("attachments");
    if (!(attachments instanceof Map<?, ?> am)) return null;
    Object data = am.get("data");
    if (!(data instanceof List<?> dl)) return null;
    for (Object d : dl) {
      if (d instanceof Map<?, ?> dm) {
        Object mediaType = dm.get("media_type");
        if (!(mediaType instanceof String mt) || !mt.toLowerCase(Locale.ROOT).startsWith("video"))
          continue; // garde "video" et "reel" (exposés comme media_type vidéo)
        Object target = dm.get("target");
        if (target instanceof Map<?, ?> tm) {
          Object id = tm.get("id");
          if (id instanceof String s && !s.isBlank()) return s;
        }
      }
    }
    return null;
  }

  private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

  private static String toUtcIsoInstant(Instant i) {
    return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString();
  }

  private static String str(Object o) { return (o == null) ? null : String.valueOf(o); }

  private static Integer asInt(Object o) {
    if (o == null) return null;
    if (o instanceof Integer i) return i;
    if (o instanceof Long l) return (int) l.longValue();
    if (o instanceof Double d) return (int) Math.round(d);
    try { return Integer.parseInt(String.valueOf(o)); }
    catch (Exception e) { return null; }
  }

  private static Instant parseIso(String s) {
    if (s == null) return null;
    try { return Instant.parse(s); } catch (Exception ignore) { }
    try {
      var fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
      return java.time.OffsetDateTime.parse(s, fmt).toInstant();
    } catch (Exception ignore) { }
    try {
      if (s.matches(".*[+-]\\d{4}$")) {
        s = s.substring(0, s.length()-2) + ":" + s.substring(s.length()-2);
        return Instant.parse(s);
      }
    } catch (Exception ignore) { }
    return null;
  }
}
