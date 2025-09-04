package ch.mno.ugo2.facebook;

import ch.mno.ugo2.config.FacebookProps;
import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import ch.mno.ugo2.facebook.dto.FbInsights;
import ch.mno.ugo2.facebook.dto.FbPagePost;
import ch.mno.ugo2.facebook.dto.FbVideo;
import ch.mno.ugo2.facebook.dto.GraphPage;
import ch.mno.ugo2.service.WebApiSinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline lisible :
 *  discover posts -> extract videoIds -> fetch video details -> upsert sources
 *  -> fetch insights -> upsert metrics (baseline @published + snapshot @now)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacebookCollectorService {

  private final ch.mno.ugo2.facebook.FacebookClient fb;
  private final FacebookProps cfg;
  private final WebApiSinkService sink;
  private final FacebookMapper mapper;

  /**
   * Collecte glissante (initial ou rolling).
   * @return nombre de vidéos traitées
   */
  public int collect(boolean initial) {
    final int window = initial ? cfg.getWindowDaysInitial() : cfg.getWindowDaysRolling();
    final OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusDays(window);
    final OffsetDateTime now  = OffsetDateTime.now(ZoneOffset.UTC);

    int totalVideos = 0;
    for (String pageId : cfg.getPageIds()) {
      totalVideos += collectPage(pageId, from, now);
    }
    log.info("Facebook collect → pages={}, videosProcessed={}", cfg.getPageIds().size(), totalVideos);
    return totalVideos;
  }

  private int collectPage(String pageId, OffsetDateTime from, OffsetDateTime now) {
    int processedVideos = 0;
    String after = null;

    do {
      // 1) Page de posts publiés (fabrique statique → lisible)
      var qPosts = FacebookQuery.buildQueryPublishedPosts(cfg, pageId, after);

      GraphPage<FbPagePost> page = fb.get(qPosts,
                      new ParameterizedTypeReference<GraphPage<FbPagePost>>() {})
              .block();

      if (page == null || page.data() == null || page.data().isEmpty()) break;

      // 2) Extraire les videoIds depuis les attachments
      List<String> videoIds = extractVideoIds(page.data());

      if (!videoIds.isEmpty()) {
        // 3) Charger les détails vidéos
        List<FbVideo> videos = fetchVideos(videoIds);

        // 4) Upsert sources (FIX: appeler méthode exposée par le sink)
        List<SourceUpsertItem> sources = videos.stream()
                .map(v -> mapper.toSource(v, "VIDEO")) // TODO: affiner pour REEL si besoin
                .toList();
        if (!sources.isEmpty()) {
          sink.batchUpsertSources(sources); // ← méthode ajoutée dans WebApiSinkService ci-dessous
        }

        // 5) Insights + metrics (baseline @published + snapshot @now)
        List<MetricsUpsertItem> metrics = new ArrayList<>(videos.size() * 2);
        for (FbVideo v : videos) {
          var qIns = FacebookQuery.buildQueryInsights(cfg, v.id());
          FbInsights ins = fb.get(qIns, FbInsights.class).block();

          // snapshot courant
          metrics.add(mapper.toSnapshot(v, ins, now));
          // baseline @ published_at si connu
          if (v.createdTime() != null && !v.createdTime().isAfter(now)) {
            metrics.add(mapper.toSnapshot(v, ins, v.createdTime()));
          }
        }
        if (!metrics.isEmpty()) {
          sink.batchUpsertMetrics(metrics); // idem: méthode exposée par le sink
        }

        processedVideos += videos.size();
      }

      after = (page.paging() != null && page.paging().cursors() != null)
              ? page.paging().cursors().after()
              : null;

    } while (after != null);

    log.info("Facebook collect page={} → processedVideos={}", pageId, processedVideos);
    return processedVideos;
  }

  private List<String> extractVideoIds(List<FbPagePost> posts) {
    return posts.stream()
            .map(FbPagePost::attachments)
            .filter(Objects::nonNull)
            .flatMap(a -> a.data().stream())
            .filter(att -> "video".equalsIgnoreCase(att.mediaType()))
            .map(att -> att.target() != null ? att.target().id() : null)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
  }

  private List<FbVideo> fetchVideos(List<String> videoIds) {
    List<FbVideo> out = new ArrayList<>(videoIds.size());
    for (String id : videoIds) {
      var qVideo = FacebookQuery.buildQueryVideo(cfg, id);
      FbVideo v = fb.get(qVideo, FbVideo.class).block();
      if (v != null) out.add(v);
    }
    return out;
  }
}
