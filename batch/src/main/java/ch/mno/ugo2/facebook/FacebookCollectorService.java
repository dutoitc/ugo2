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
   * Collecte sur fenêtre glissante:
   *  - upsert des sources
   *  - métriques:
   *      * baseline 0 @ published_at (si connu)
   *      * snapshot courant @ now UTC (views_3s réelles si dispo)
   *
   * @return nombre de snapshots envoyés
   */
  public int collect() {
    final int daysBack = cfg.getWindowDaysRolling();
    final Instant cutoff = Instant.now().minus(daysBack, ChronoUnit.DAYS);

    Set<String> seenVideoIds = new HashSet<>();
    List<SourceUpsertItem> sources = new ArrayList<>();
    List<MetricsUpsertItem> metrics = new ArrayList<>();

    int pushed = 0;

    for (String pageId : cfg.getPageIds()) {
      log.info("FB: scanning page {} (daysBack={})", pageId, daysBack);

      // 1) Posts publiés avec médias (du plus récent au plus ancien)
      String postFields = String.join(",",
              "id",
              "created_time",
              "permalink_url",
              "attachments{media_type,target{id}}"
      );
      List<Map<String, Object>> posts = fb.listPublishedPosts(pageId, postFields, cfg.getPageSize(), cfg.getAccessToken());
      if (posts == null) continue;

      for (Map<String, Object> post : posts) {
        String createdTime = str(post.get("created_time"));
        Instant created = parseIso(createdTime);
        if (created != null && created.isBefore(cutoff)) {
          // Stop: on a dépassé la fenêtre
          break;
        }

        String videoId = extractVideoId(post);
        if (videoId == null) continue;
        if (!seenVideoIds.add(videoId)) continue; // déjà fait

        // 2) Détails vidéo
        Map<String, Object> video = fb.get("/" + videoId, cfg.getAccessToken(),
                "id,length,description,title,created_time,permalink_url");
        if (video == null) continue;

        String title = str(video.get("title"));
        String desc  = str(video.get("description"));
        String vPermalink = str(video.get("permalink_url"));
        String vCreated   = str(video.get("created_time"));
        Integer durationSec = asInt(video.get("length"));

        boolean isTeaser = ch.mno.ugo2.util.TeaserHeuristics.isTeaser(title, desc, durationSec);
        String postPermalink = str(post.get("permalink_url"));

        // Source: published_at
        Instant pub = parseFbDate(vCreated != null ? vCreated : createdTime);

        sources.add(SourceUpsertItem.builder()
                .platform("FACEBOOK")
                .platform_source_id(videoId)
                .title(title)
                .description(desc)
                .permalink_url(notBlank(vPermalink) ? vPermalink : postPermalink)
                .media_type("VIDEO")
                .duration_seconds(durationSec)
                .published_at(toUtcIsoInstant(pub)) // DB en UTC
                .is_teaser(isTeaser ? 1 : 0)
                .locked(0)
                .build());

        // 3) Métriques:
        // 3a) baseline 0 @ published_at (si pub connue)
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

        // 3b) snapshot courant @ now (views3s réelles si dispo)
        Integer views3s = fetchLifetimeViews(videoId); // ta référence 3s
        MetricsUpsertItem.MetricsUpsertItemBuilder mb = MetricsUpsertItem.builder()
                .platform("FACEBOOK")
                .platform_source_id(videoId)
                .captured_at(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString());

        if (views3s != null) {
          mb.views_3s(views3s).views_platform_raw(views3s);
        }
        // on laisse comments/shares/reactions/saves à null pour l’instant
        metrics.add(mb.build());

        if (sources.size() >= cfg.getMaxVideosPerRun()) {
          log.warn("FB: safety stop at {} videos", sources.size());
          break;
        }
      }
    }

    // Push (sources avant métriques)
    sink.pushSources(sources);
    sink.pushMetrics(metrics);
    pushed = metrics.size();

    log.info("FB pushed sources={}, metrics={}", sources.size(), pushed);
    return pushed;
  }

  private Integer fetchLifetimeViews(String videoId) {
    Map<String, Object> resp = fb.get("/" + videoId + "/video_insights/total_video_views/lifetime",
            cfg.getAccessToken(), null);
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

  private static String extractVideoId(Map<String, Object> post) {
    Object attachments = post.get("attachments");
    if (!(attachments instanceof Map<?, ?> am)) return null;
    Object data = am.get("data");
    if (!(data instanceof List<?> dl)) return null;
    for (Object d : dl) {
      if (d instanceof Map<?, ?> dm) {
        Object mediaType = dm.get("media_type");
        if (!(mediaType instanceof String mt) || !mt.startsWith("video")) continue;
        Object target = dm.get("target");
        if (target instanceof Map<?, ?> tm) {
          Object id = tm.get("id");
          if (id instanceof String s && !s.isBlank()) return s;
        }
      }
    }
    return null;
  }

  private static Instant parseFbDate(String s) {
    if (s == null || s.isBlank()) return null;
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

  private static Instant parseIso(String iso) {
    try { return (iso == null) ? null : Instant.parse(iso); }
    catch (Exception e) { return null; }
  }

  private static String toUtcIsoInstant(Instant i) {
    return i == null ? null : i.truncatedTo(ChronoUnit.SECONDS).toString();
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

  private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
