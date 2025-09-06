package ch.mno.ugo2.service;

import ch.mno.ugo2.facebook.FacebookApiException;
import ch.mno.ugo2.facebook.FacebookCollectorService;
import ch.mno.ugo2.youtube.YouTubeCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

  private final YouTubeCollectorService yt;   // doit exposer int collect()
  private final FacebookCollectorService fb;  // doit exposer int collect(boolean fullScan)

  /** Retourne le nombre total de snapshots poussés (YT + FB). */
  public int discover() {
    int pushed = 0;
    pushed += discoverYT();
    pushed += discoverFB();
    return pushed;
  }

  private int discoverFB() {
    int fbPushed = 0;
    try {
      fbPushed = fb.collect();
    } catch (FacebookApiException e) {
      log.error("[discovery] Facebook error: {}", e.getMessage());
    } catch (Exception e) {
      log.error("[discovery] Facebook unexpected error", e);
    }
    log.info("[discovery] Facebook pushed snapshots={}", fbPushed);
    return fbPushed;
  }

  private int discoverYT() {
    int ytPushed = 0;
    try {
      ytPushed = yt.collect();  // fenêtre & logique internes au collector
    } catch (Exception e) {
      log.error("[discovery] YouTube unexpected error", e);
    }
    log.info("[discovery] YouTube pushed snapshots={}", ytPushed);
    return ytPushed;
  }
}
