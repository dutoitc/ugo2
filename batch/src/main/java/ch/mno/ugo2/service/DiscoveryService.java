package ch.mno.ugo2.service;

import ch.mno.ugo2.facebook.FacebookApiException;
import ch.mno.ugo2.youtube.YouTubeCollectorService;
import ch.mno.ugo2.facebook.FacebookCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {
  private final YouTubeCollectorService yt;
  private final FacebookCollectorService fb;

  /** Retourne le nombre de snapshots poussés (YT + FB) */
  public int discover() {
    int pushed = 0;

    int ytPushed = yt.collect();
    log.info("[discovery] YouTube pushed snapshots={}", ytPushed);
    pushed += ytPushed;

    int fbPushed = 0;
    try {
      fbPushed = fb.collect();
    } catch (FacebookApiException e) {
      log.error("[discovery] Facebook error: {}", e.getMessage());
    } catch (Exception e) {
      log.error("[discovery] Facebook unexpected error", e);
    }
    log.info("[discovery] Facebook pushed snapshots={}", fbPushed);
    pushed += fbPushed;

    return pushed;
  }
}
