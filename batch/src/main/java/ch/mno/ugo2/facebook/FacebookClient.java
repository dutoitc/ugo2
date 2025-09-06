package ch.mno.ugo2.facebook;

import ch.mno.ugo2.common.AbstractClient;
import ch.mno.ugo2.facebook.dto.FbError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import ch.mno.ugo2.facebook.responses.FacebookPostsResponse;
import ch.mno.ugo2.facebook.responses.InsightsResponse;
import ch.mno.ugo2.facebook.responses.VideoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Client Graph API minimaliste et typé.
 * - Centralise la gestion des erreurs
 * - Backoff sur 429/IO
 * - Deux overloads: Class<T> et ParameterizedTypeReference<T>
 */
@Slf4j
@Component
public class FacebookClient extends AbstractClient {

    private static final String BASE = "https://graph.facebook.com";

    public FacebookClient() {
        super(WebClient.builder().build());
    }

    private Retry retrySpec() {
        return Retry.backoff(3, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(15))
                .jitter(0.2);
    }

    public Mono<FacebookPostsResponse> publishedPosts(String version, String pageId,
                                                      String accessToken, Integer limit,
                                                      String after, String since, String until) {
        String fields = "id,created_time,permalink_url,attachments%7Bmedia_type,target%7D";

        URI uri = UriComponentsBuilder
                .fromHttpUrl(BASE + "/" + version + "/" + pageId + "/published_posts")
                .queryParam("fields", fields)     // already encoded
                .queryParam("limit", Optional.ofNullable(limit).orElse(100))
                .queryParam("access_token", accessToken)
//                .queryParam("since", since)  // TODO: format
//                .queryParam("until", until)  // TODO: format
                .queryParamIfPresent("after", Optional.ofNullable(after))
                .build(true)                      // ← tells Spring “don’t re-encode”
                .toUri();
        return get(uri, null, FacebookPostsResponse.class).retryWhen(retrySpec());
    }

    public Mono<VideoResponse> video(String version, String videoId, String accessToken) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE + "/" + version + "/" + videoId)
                .queryParam("fields", "id,title,description,permalink_url,created_time,length")
                .queryParam("access_token", accessToken)
                .build(true).toUri();
        log.debug("Calling {}", uri);
        return get(uri, null, VideoResponse.class).retryWhen(retrySpec());
    }

    public Mono<InsightsResponse> insights(String version, String videoId, String accessToken) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE + "/" + version + "/" + videoId + "/video_insights")
                .queryParam("access_token", accessToken)
                .build(true).toUri();
        log.debug("Calling {}", uri);
        return get(uri, null, InsightsResponse.class).retryWhen(retrySpec());
    }

//
//    public <T> reactor.core.publisher.Mono<T> get(FacebookQuery q, Class<T> type) {
//        return get(q.getPath(), q.getParams(), type);
//    }
//
//    public <T> reactor.core.publisher.Mono<T> get(FacebookQuery q, org.springframework.core.ParameterizedTypeReference<T> type) {
//        return get(q.getPath(), q.getParams(), type);
//    }
//
//    private <T> Mono<T> get(String path, Map<String, String> query, Class<T> type) {
//        return http.get()
//                .uri(b -> {
//                    var ub = b.path(path);
//                    if (query != null) {
//                        query.forEach((k, v) -> {
//                            if (v != null && !v.isBlank()) ub.queryParam(k, v);
//                        });
//                    }
//                    return ub.build();
//                })
//                .retrieve()
//                .onStatus(HttpStatusCode::isError, resp ->
//                        resp.bodyToMono(String.class).flatMap(body -> {
//                            FbError err = safeParse(body, FbError.class);
//                            var ex = new FacebookApiException(
//                                    err != null ? err.message() : "Facebook API error",
//                                    err != null ? err.code() : null,
//                                    err != null ? err.type() : null,
//                                    err != null ? err.fbtrace_id() : null,
//                                    body
//                            );
//                            log.error("FB GET {} failed: {}", path, ex.toString());
//                            return Mono.error(ex);
//                        })
//                )
//                .bodyToMono(type)
//                .retryWhen(retrySpec());
//    }
//
//    private <T> Mono<T> get(String path, Map<String, String> query, ParameterizedTypeReference<T> type) {
//        return http.get()
//                .uri(b -> {
//                    var ub = b.path(path);
//                    if (query != null) {
//                        query.forEach((k, v) -> {
//                            if (v != null && !v.isBlank()) ub.queryParam(k, v);
//                        });
//                    }
//                    return ub.build();
//                })
//                .retrieve()
//                .onStatus(HttpStatusCode::isError, resp ->
//                        resp.bodyToMono(String.class).flatMap(body -> {
//                            FbError err = safeParse(body, FbError.class);
//                            var ex = new FacebookApiException(
//                                    err != null ? err.message() : "Facebook API error",
//                                    err != null ? err.code() : null,
//                                    err != null ? err.type() : null,
//                                    err != null ? err.fbtrace_id() : null,
//                                    body
//                            );
//                            log.error("FB GET {} failed: {}", path, ex.toString());
//                            return Mono.error(ex);
//                        })
//                )
//                .bodyToMono(type)
//                .retryWhen(retrySpec());
//    }


//    private Retry retrySpec() {
//        return Retry.backoff(3, Duration.ofSeconds(2))
//                .maxBackoff(Duration.ofSeconds(15))
//                .jitter(0.2)
//                .filter(t ->
//                        t instanceof WebClientResponseException.TooManyRequests ||
//                                t instanceof IOException
//                )
//                .onRetryExhaustedThrow((spec, sig) -> sig.failure());
//    }


}
