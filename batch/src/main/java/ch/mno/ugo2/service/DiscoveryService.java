package ch.mno.ugo2.service;

import ch.mno.ugo2.youtube.YouTubeCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {
  private final YouTubeCollectorService yt;

  public int discover(boolean initial) {
    int pushed = yt.collect(initial);
    log.info("[discovery] YouTube pushed snapshots={}", pushed);
    return pushed;
  }
}
