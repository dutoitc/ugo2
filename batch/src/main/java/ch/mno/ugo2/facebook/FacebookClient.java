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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    public Mono<FacebookPostsResponse> publishedPosts(
            String version,
            String pageId,
            String accessToken,
            Integer limit,
            String after
    ) {
        String fields = "id,created_time,permalink_url,attachments%7Bmedia_type,target,subattachments,media%7D";

        URI uri = UriComponentsBuilder
                .fromHttpUrl(BASE + "/" + version + "/" + pageId + "/published_posts")
                .queryParam("fields", fields)
                .queryParam("limit", Optional.ofNullable(limit).orElse(100))
                .queryParam("access_token", accessToken)
                .queryParamIfPresent("after", Optional.ofNullable(after))
                .build(true)
                .toUri();

        log.debug("Calling {}", uri);
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

    public Mono<List<String>> videosUploaded(String version, String pageId, String accessToken) {
        return fetchVideosPaginated(version, pageId, accessToken, "uploaded");
    }

    public Mono<List<String>> videosReels(String version, String pageId, String accessToken) {
        return fetchVideosPaginated(version, pageId, accessToken, "reels");
    }

    private Mono<List<String>> fetchVideosPaginated(
            String version,
            String pageId,
            String accessToken,
            String type
    ) {
        List<String> out = new ArrayList<>();

        return Flux.generate(
                        () -> null,   // état initial = after=null
                        (after, sink) -> {

                            URI uri = UriComponentsBuilder
                                    .fromHttpUrl(BASE + "/" + version + "/" + pageId + "/videos")
                                    .queryParam("type", type)
                                    .queryParam("fields", "id")
                                    .queryParam("limit", 100)
                                    .queryParam("access_token", accessToken)
                                    .queryParamIfPresent("after", Optional.ofNullable(after))
                                    .build(true)
                                    .toUri();

                            log.debug("Calling {}", uri);

                            try {
                                Map json = get(uri, null, Map.class).block();

                                // --- DATA
                                List<Map<String, Object>> data = (List<Map<String, Object>>) json.get("data");
                                if (data != null) {
                                    for (var item : data) {
                                        Object id = item.get("id");
                                        if (id != null) out.add(id.toString());
                                    }
                                }

                                // --- PAGING
                                String nextAfter = null;
                                Map<String, Object> paging = (Map<String, Object>) json.get("paging");
                                if (paging != null) {
                                    Map<String, Object> cursors =
                                            (Map<String, Object>) paging.get("cursors");
                                    if (cursors != null && cursors.get("after") != null) {
                                        nextAfter = cursors.get("after").toString();
                                    }
                                }

                                if (nextAfter == null) {
                                    sink.complete();
                                } else {
                                    sink.next(nextAfter);
                                }

                                return nextAfter;
                            } catch (Exception ex) {
                                sink.error(ex);
                                return after;
                            }
                        })
                .then(Mono.fromCallable(() -> out))
                .retryWhen(retrySpec());
    }




    public Mono<InsightsResponse> insights(String version, String videoId, String accessToken) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE + "/" + version + "/" + videoId + "/video_insights")
                .queryParam("access_token", accessToken)
                .build(true).toUri();
        log.debug("Calling {}", uri);
        return get(uri, null, InsightsResponse.class).retryWhen(retrySpec());
    }


}
