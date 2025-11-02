package ch.mno.ugo2.instagram;

import ch.mno.ugo2.api.InstagramClient;
import ch.mno.ugo2.config.InstagramProps;
import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import ch.mno.ugo2.service.WebApiSinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramCollectorService {

  private final InstagramProps cfg;
  private final InstagramClient ig;
  private final WebApiSinkService sink;

  /**
   * Collecte les médias IG (VIDEO/REEL) et pousse sources + métriques.
   * Fenêtre: windowDaysRolling par défaut.
   *
   * @return nombre de snapshots poussés
   */
  public int collect() {
    int days = Math.max(1, cfg.getWindowDaysRolling());
    Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
    return collectInternal(since);
  }

  public int collectFullScan() {
    int days = Math.max(1, cfg.getWindowDaysInitial());
    Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
    return collectInternal(since);
  }

  int collectInternal(Instant since) {
    String token = StringUtils.trimToNull(cfg.getAccessToken());
    if (token == null) {
      log.warn("[IG] no access token configured, skip");
      return 0;
    }
    List<String> users = Optional.ofNullable(cfg.getUserIds()).orElseGet(List::of);
    if (users.isEmpty()) {
      log.warn("[IG] no userIds configured, skip");
      return 0;
    }

    String v = StringUtils.defaultIfBlank(cfg.getApiVersion(), "v23.0");
    int limit = Math.max(1, Math.min(cfg.getPageSize(), 100));
    int cap = Math.max(1, cfg.getMaxMediaPerRun());

    List<SourceUpsertItem> sources = new ArrayList<>();
    List<MetricsUpsertItem> snapshots = new ArrayList<>();

    for (String uid: users) {
      int pushedForUser = 0;
      try {
        // Champs: suffisent pour construire Source & premiers compteurs
        String fields = "id,caption,media_type,media_product_type,permalink,thumbnail_url,media_url,timestamp,like_count,comments_count,video_view_count";
        URI first = UriComponentsBuilder.fromHttpUrl("https://graph.facebook.com/" + v + "/" + uid + "/media")
                .queryParam("fields", fields)
                .queryParam("limit", limit)
                .queryParam("access_token", token)
                .build(true).toUri();

    List<Map<String,Object>> items = ig.listMedia(first.toString(), /*maxPages*/ 50);
        for (Map<String,Object> m: items) {
          String mediaType = str(m.get("media_type"));
          String product = str(m.get("media_product_type"));
          String id = str(m.get("id"));
          String permalink = str(m.get("permalink"));
          String caption = str(m.get("caption"));
          Instant ts = parseTs(str(m.get("timestamp")));
          if (id == null || ts == null) continue;
          if (ts.isBefore(since)) continue;

          String type = mapType(mediaType, product);
          if (!"VIDEO".equals(type) && !"REEL".equals(type)) continue;

          // Likes & comments & views (si dispo)
          Long likes = toLong(m.get("like_count"));
          Long comments = toLong(m.get("comments_count"));
          Long views= 0L;
          Long shares = 0L;
            Long reach = 0L;

            // Insights par média pour compléter/fiabiliser les compteurs (le /media seul est insuffisant)
            try {
                URI insightsUrl = UriComponentsBuilder.fromHttpUrl("https://graph.facebook.com/" + v + "/" + id + "/insights")
                        .queryParam("metric", "views,reach,saved,likes,comments,shares,total_interactions")
                        .queryParam("access_token", token)
                        .build(true).toUri();
                Map<String, Long> ins = ig.readInsights(insightsUrl.toString());
                if (ins != null && !ins.isEmpty()) {
                    views = ins.get("views");
                    likes = ins.get("likes");
                    comments = ins.get("comments");
                    shares = ins.get("shares");
                    reach = ins.get("reach");
                }
            } catch (Exception e) {
                // on continue avec les valeurs déjà présentes
            }

          sources.add(SourceUpsertItem.builder()
                  .platform("INSTAGRAM")
                  .platform_source_id(id)
                  .title(truncate(caption, 140))
                  .description(caption)
                  .permalink_url(permalink)
                  .media_type(type)
                  .published_at(ts.toString())
//                  .is_teaser(null)
//                  .video_id(null)
                  .locked(null)
                  .build());

          snapshots.add(MetricsUpsertItem.builder()
                  .platform("INSTAGRAM")
                  .platform_format(type)
                  .platform_video_id(id)
                  .snapshot_at(Instant.now())
                  .views_native(views)
                  .likes(likes)
                  .comments(comments)
                  .shares(shares)
                          .reach(reach)
                  .build());

          pushedForUser++;
          if (pushedForUser >= cap) break;
        }
      } catch (Exception e) {
        log.warn("[IG] user={} error: {}", uid, e.toString());
      }
    }

    // Push (bloquant)
    log.info("[IG] upsert {} sources, {} metrics", sources.size(), snapshots.size());
    sink.batchUpsertSources(sources);
    sink.batchUpsertMetrics(snapshots);
    return snapshots.size();
  }

  private static String mapType(String mediaType, String product) {
    // media_type: IMAGE|VIDEO|CAROUSEL_ALBUM; media_product_type: FEED|STORY|REELS|AD|...

      if ("VIDEO".equalsIgnoreCase(mediaType)) return "VIDEO";
      if ("REELS".equalsIgnoreCase(product) || "REEL".equals(product)) return "REEL";
    return "POST";
  }

  private static String truncate(String s, int n) {
    if (s == null) return null;
    if (s.length() <= n) return s;
    return s.substring(0, Math.max(0, n));
  }

  private static String str(Object o) { return o == null ? null : String.valueOf(o); }

  private static Long toLong(Object o) {
    if (o == null) return null;
    try { return Long.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
  }

    private static Instant parseTs(String iso) {
        if (StringUtils.isBlank(iso)) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            // Fallback pour format Instagram: "yyyy-MM-dd'T'HH:mm:ssZ" (ex: 2025-10-01T11:46:23+0000)
            try {
                java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
                return java.time.OffsetDateTime.parse(iso, f).toInstant();
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
