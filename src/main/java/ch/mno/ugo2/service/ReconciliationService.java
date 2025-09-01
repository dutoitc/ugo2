package ch.mno.ugo2.service;

import ch.mno.ugo2.config.AppProps;
import ch.mno.ugo2.model.SourceVideo;
import ch.mno.ugo2.model.Video;
import ch.mno.ugo2.reconcile.Clusterer;
import ch.mno.ugo2.reconcile.MatchingScorer;
import ch.mno.ugo2.reconcile.TeaserHeuristics;
import ch.mno.ugo2.repo.SourceVideoRepository;
import ch.mno.ugo2.repo.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

  private final SourceVideoRepository sourceRepo;
  private final VideoRepository videoRepo;
  private final AppProps appProps;

  public ReconciliationService(SourceVideoRepository sourceRepo,
                               VideoRepository videoRepo,
                               AppProps appProps) {
    this.sourceRepo = sourceRepo;
    this.videoRepo = videoRepo;
    this.appProps = appProps;
  }

  public record ResultSummary(int clusters, int createdVideos, int linkedSources, int skippedLocked) {}

  @Transactional
  public ResultSummary reconcile(LocalDateTime from, LocalDateTime to, boolean dryRun) {
    List<SourceVideo> candidates = new ArrayList<>();
    sourceRepo.findByPublishedBetween(from, to).forEach(candidates::add);
    if (candidates.isEmpty()) return new ResultSummary(0, 0, 0, 0);

    AppProps.Reconcile cfg = appProps.getReconcile();
    Clusterer clusterer = new Clusterer(cfg.threshold);
    MatchingScorer scorer = new MatchingScorer(cfg);
    List<List<SourceVideo>> clusters = clusterer.cluster(candidates, scorer);

    int created = 0, linked = 0, lockedSkip = 0;

    for (List<SourceVideo> cluster : clusters) {
      LocalDateTime earliest = cluster.stream()
          .map(SourceVideo::getPublishedAt)
          .filter(Objects::nonNull)
          .min(LocalDateTime::compareTo).orElse(null);
      long earliestEpoch = earliest!=null ? earliest.toEpochSecond(java.time.ZoneOffset.UTC) : 0L;

      Long chosenVideoId = cluster.stream()
          .map(SourceVideo::getVideoId).filter(Objects::nonNull)
          .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toSet(), set -> set.isEmpty()? null : set.iterator().next()));

      Video video;
      if (chosenVideoId == null) {
        video = new Video();
        video.setCanonicalTitle(cluster.stream().map(SourceVideo::getTitle).filter(Objects::nonNull).findFirst().orElse(""));
        video.setCanonicalDescription(cluster.stream().map(SourceVideo::getDescription).filter(Objects::nonNull).findFirst().orElse(null));
        video.setOfficialPublishedAt(earliest);
        if (!dryRun) video = videoRepo.save(video);
        created++;
      } else {
        video = videoRepo.findById(chosenVideoId).orElseGet(() -> { Video v = new Video(); v.setId(chosenVideoId); return v; });
        if (video.getOfficialPublishedAt()==null || (earliest!=null && earliest.isBefore(video.getOfficialPublishedAt()))) {
          video.setOfficialPublishedAt(earliest);
          if (!dryRun) videoRepo.save(video);
        }
      }

      TeaserHeuristics heur = new TeaserHeuristics(cfg);
      for (SourceVideo s : cluster) {
        boolean isLocked = false;
        try {
          var m = s.getClass().getDeclaredMethod("isLocked");
          isLocked = (boolean) m.invoke(s);
        } catch (Exception ignore) {}
        if (isLocked) { lockedSkip++; continue; }

        boolean teaser = s.isTeaser() || heur.isTeaser(s, earliestEpoch);
        if (!dryRun) {
          s.setTeaser(teaser);
          s.setVideoId(video.getId());
          sourceRepo.save(s);
        }
        linked++;
      }
    }

    return new ResultSummary(clusters.size(), created, linked, lockedSkip);
  }
}
