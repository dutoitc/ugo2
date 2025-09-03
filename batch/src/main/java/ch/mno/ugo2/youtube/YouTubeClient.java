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

    /**
     * GET JSON avec prise en charge du 304:
     * - on tente avec If-None-Match si etag != null
     * - si 304 Not Modified, on refait la requête SANS ETag pour récupérer le corps
     */
    private Mono<JsonNode> getJson(URI uri, String etag) {
        String safeUri = uri.toString().replaceAll("([?&]key=)[^&]+", "$1***");
        log.debug("GET {}", safeUri);

        return http.get()
                .uri(uri)
                .headers(h -> { if (etag != null) h.set(HttpHeaders.IF_NONE_MATCH, etag); })
                .exchangeToMono(resp -> {
                    int code = resp.statusCode().value();

                    // 2xx -> lire le corps
                    if (resp.statusCode().is2xxSuccessful()) {
                        return resp.bodyToMono(String.class)
                                .map(this::toJsonNode);
                    }

                    // 304 -> fallback sans ETag
                    if (code == HttpStatus.NOT_MODIFIED.value()) {
                        log.debug("HTTP 304 Not Modified — fallback GET without ETag");
                        return http.get()
                                .uri(uri)
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(this::toJsonNode);
                    }

                    // autres erreurs -> message lisible
                    return resp.bodyToMono(String.class).defaultIfEmpty("")
                            .flatMap(b -> Mono.error(new RuntimeException("YouTube API error " + code + " body=" + b)));
                })
                .timeout(Duration.ofSeconds(20));
    }

    private JsonNode toJsonNode(String body) {
        try { return M.readTree(body); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    /** channels.list — part=contentDetails */
    public Mono<JsonNode> channelsContentDetails(String apiKey, String channelId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE + "/channels")
                .queryParam("part", "contentDetails")
                .queryParam("id", channelId)
                .queryParam("key", apiKey)
                .build(true)
                .toUri();
        // pas d'ETag ici, c’est léger
        return getJson(uri, null);
    }

    /** playlistItems.list — part=contentDetails,snippet ; gère ETag via getJson() */
    public Mono<JsonNode> playlistItems(String apiKey, String playlistId, Integer maxResults, String pageToken, String etag) {
        int page = (maxResults == null ? 50 : maxResults);
        page = Math.max(1, Math.min(50, page)); // Java n’a pas Math.clamp

        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(BASE + "/playlistItems")
                .queryParam("part", "contentDetails,snippet")
                .queryParam("playlistId", playlistId)
                .queryParam("maxResults", page)
                .queryParam("key", apiKey);
        if (pageToken != null) b.queryParam("pageToken", pageToken);

        URI uri = b.build(true).toUri();
        return getJson(uri, etag);
    }

    /** videos.list — part=snippet,statistics,contentDetails (IDs en CSV) */
    public Mono<JsonNode> videosList(String apiKey, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Mono.just(M.createObjectNode().putArray("items"));
        }
        String joined = String.join(",", ids);
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE + "/videos")
                .queryParam("part", "snippet,statistics,contentDetails")
                .queryParam("id", joined)
                .queryParam("key", apiKey)
                .build(true)
                .toUri();
        // pas d’ETag ici pour fiabiliser la collecte des stats
        return getJson(uri, null);
    }
}
