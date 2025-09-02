package ch.mno.ugo2.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class YouTubeClient {

    private static final String BASE = "https://www.googleapis.com/youtube/v3";
    private final ObjectMapper M = new ObjectMapper();

    private final WebClient http = WebClient.builder()
            .filter(maskedRequestLog())
            .filter(logResponse())
            .build();

    private static ExchangeFilterFunction maskedRequestLog() {
        return ExchangeFilterFunction.ofRequestProcessor((ClientRequest req) -> {
            String u = req.url().toString().replaceAll("([?&]key=)[^&]+", "$1***");
            log.debug("HTTP -> {} {}", req.method(), u);
            return Mono.just(req);
        });
    }

    private static ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor((ClientResponse resp) -> {
            log.debug("HTTP <- {}", resp.statusCode().value());
            return Mono.just(resp);
        });
    }

    private Mono<JsonNode> getJson(URI uri, String etag) {
        log.debug("GET {}", uri.toString().replaceAll("([?&]key=)[^&]+", "$1***"));
        return http.get()
                .uri(uri)
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
        if (code == HttpStatus.NOT_MODIFIED.value()) {
            log.debug("HTTP 304 Not Modified");
            return Mono.empty();
        }
        return resp.bodyToMono(String.class).defaultIfEmpty("")
                .flatMap(b -> Mono.error(new RuntimeException("YouTube API error " + code + " body=" + b)));
    }

    /** channels.list — part=contentDetails */
    public Mono<JsonNode> channelsContentDetails(String apiKey, String channelId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE + "/channels")
                .queryParam("part", "contentDetails")
                .queryParam("id", channelId)
                .queryParam("key", apiKey)
                .build(true)
                .toUri();
        return getJson(uri, null);
    }

    /** playlistItems.list — part=contentDetails,snippet ; etag géré par l’appelant */
    public Mono<JsonNode> playlistItems(String apiKey, String playlistId, Integer maxResults, String pageToken, String etag) {
        int page = Math.clamp(maxResults == null ? 50 : maxResults, 1, 50);
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(BASE + "/playlistItems")
                .queryParam("part", "contentDetails,snippet")
                .queryParam("playlistId", playlistId)
                .queryParam("maxResults", page)
                .queryParam("key", apiKey);
        if (pageToken != null) b.queryParam("pageToken", pageToken);
        URI uri = b.build(true).toUri();
        return getJson(uri, etag);
    }

    /** videos.list — part=snippet,statistics,contentDetails (pas d’encodage manuel des IDs) */
    public Mono<JsonNode> videosList(String apiKey, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Mono.just(M.createObjectNode().putArray("items"));
        }
        String joined = String.join(",", ids);
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE + "/videos")
                .queryParam("part", "snippet,statistics,contentDetails")
                .queryParam("id", joined)  // UriComponentsBuilder encode proprement (pas de %252C)
                .queryParam("key", apiKey)
                .build(true)
                .toUri();
        return getJson(uri, null);
    }
}
