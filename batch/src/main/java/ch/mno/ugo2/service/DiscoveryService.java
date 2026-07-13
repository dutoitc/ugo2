package ch.mno.ugo2.service;

import ch.mno.ugo2.config.FacebookProps;
import ch.mno.ugo2.config.InstagramProps;
import ch.mno.ugo2.config.YouTubeProps;
import ch.mno.ugo2.facebook.FacebookCollectorService;
import ch.mno.ugo2.instagram.InstagramCollectorService;
import ch.mno.ugo2.youtube.YouTubeCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.function.IntSupplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private final YouTubeCollectorService yt;
    private final FacebookCollectorService fb;
    private final InstagramCollectorService ig;
    private final YouTubeProps ytProps;
    private final FacebookProps fbProps;
    private final InstagramProps igProps;
    private final WebApiSinkService sink;

    public int discover() {
        int pushed = 0;
        pushed += discoverPlatform("YOUTUBE", ytProps.getTokenExpiresAt(), yt::collect);
        pushed += discoverPlatform("FACEBOOK", fbProps.getTokenExpiresAt(), fb::collect);
        pushed += discoverPlatform("INSTAGRAM", igProps.getTokenExpiresAt(), ig::collect);
        return pushed;
    }

    private int discoverPlatform(String platform, String tokenExpiresAt, IntSupplier collector) {
        long started = System.nanoTime();
        try {
            int pushed = collector.getAsInt();
            int durationMs = elapsedMs(started);
            log.info("[discovery] {} pushed snapshots={} durationMs={}", platform, pushed, durationMs);
            reportPlatform(platform, true, durationMs, pushed, null, tokenExpiresAt, false);
            return pushed;
        } catch (Exception e) {
            int durationMs = elapsedMs(started);
            boolean tokenLikelyExpired = tokenLikelyExpired(e);
            log.error("[discovery] {} error: {}", platform, e.getMessage(), e);
            reportPlatform(platform, false, durationMs, 0, e.getMessage(), tokenExpiresAt, tokenLikelyExpired);
            return 0;
        }
    }

    private void reportPlatform(String platform, boolean success, int durationMs, int items,
                                String message, String tokenExpiresAt, boolean tokenLikelyExpired) {
        try {
            sink.reportPlatformHealth(
                    platform, success, durationMs, items, message, tokenExpiresAt, tokenLikelyExpired
            );
        } catch (Exception reportError) {
            log.warn("[discovery] health report for {} failed: {}", platform, reportError.toString());
        }
    }

    private static int elapsedMs(long startedNanos) {
        return (int)Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static boolean tokenLikelyExpired(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String text = String.valueOf(current.getMessage()).toLowerCase(Locale.ROOT);
            if (text.contains("401") || text.contains("403") || text.contains("oauth")
                    || text.contains("access token") || text.contains("invalid credential")
                    || text.contains("keyinvalid") || text.contains("expired")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
