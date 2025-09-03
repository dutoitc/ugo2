package ch.mno.ugo2.service;

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

  public int discover(boolean initial) {
    int pushed = 0;

    int ytPushed = yt.collect(initial);
    log.info("[discovery] YouTube pushed snapshots={}", ytPushed);
    pushed += ytPushed;

    int fbPushed = 0;
    try {
      fbPushed = fb.collect(initial);
    } catch (ch.mno.ugo2.facebook.FacebookApiException e) {
      // Batch continue mais le log est clair
      log.error("[discovery] Facebook error: {}", e.getMessage());
    }
    log.info("[discovery] Facebook pushed snapshots={}", fbPushed);
    pushed += fbPushed;

    return pushed;
  }
}
