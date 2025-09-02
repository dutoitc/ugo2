package ch.mno.ugo2.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class YouTubeClient {
    private static final String BASE = "https://www.googleapis.com/youtube/v3";
    private final ObjectMapper M = new ObjectMapper();
    private final WebClient http = WebClient.builder()
            // .defaultHeader("User-Agent", "webtvcompanion") // optionnel
            .build();

    private Mono<JsonNode> getJson(String url, String etag) {
        return http.get().uri(url)
                .headers(h -> { if (etag != null) h.set(HttpHeaders.IF_NONE_MATCH, etag); })
                .exchangeToMono(this::handle)
                .timeout(Duration.ofSeconds(20));
    }

    private Mono<JsonNode> handle(ClientResponse resp) {
        int code = resp.statusCode().value();
        if (resp.statusCode().is2xxSuccessful()) {
            return resp.bodyToMono(String.class).map(s -> {
                try { return M.readTree(s); } catch (Exception e) { throw new RuntimeException(e); }
            });
        }
        if (code == HttpStatus.NOT_MODIFIED.value()) { // 304
            return Mono.empty();
        }
        return resp.bodyToMono(String.class).defaultIfEmpty("")
                .flatMap(b -> Mono.error(new RuntimeException("YouTube API error " + code + " body=" + b)));
    }

    /** channels.list — on retire le param `fields` pour éviter le 400, part=contentDetails suffit. */
    public Mono<JsonNode> channelsContentDetails(String apiKey, String channelId) {
        String url = BASE + "/channels"
                + "?part=contentDetails"
                + "&id=" + enc(channelId)
                + "&key=" + enc(apiKey);
        return getJson(url, null);
    }

    /** playlistItems.list — sans `fields` (quota identique, payload + grand mais fiable). */
    public Mono<JsonNode> playlistItems(String apiKey, String playlistId, Integer maxResults, String pageToken, String etag) {
        int page = Math.clamp(maxResults == null ? 50 : maxResults, 1, 50);
        StringBuilder sb = new StringBuilder(BASE)
                .append("/playlistItems?part=contentDetails,snippet")
                .append("&playlistId=").append(enc(playlistId))
                .append("&maxResults=").append(page)
                .append("&key=").append(enc(apiKey));
        if (pageToken != null) sb.append("&pageToken=").append(enc(pageToken));
        return getJson(sb.toString(), etag);
    }

    /** videos.list — sans `fields` pour fiabilité. */
    public Mono<JsonNode> videosList(String apiKey, List<String> ids) {
        if (ids.isEmpty()) return Mono.just(M.createObjectNode().putArray("items"));
        String url = BASE + "/videos"
                + "?part=snippet,statistics,contentDetails"
                + "&id=" + enc(String.join(",", ids))
                + "&key=" + enc(apiKey);
        return getJson(url, null);
    }

    private static String enc(String s){ return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
