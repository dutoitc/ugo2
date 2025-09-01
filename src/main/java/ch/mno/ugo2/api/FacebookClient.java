package ch.mno.ugo2.api;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Facebook Graph v23 client (minimal stub).
 * Endpoints:
 * - /{page-id}/published_posts?fields=id,created_time,permalink_url,attachments{media_type,target{id}}&limit=100
 * - /{video-id}?fields=length,description,title,created_time,permalink_url
 * - /{video-id}/video_insights/total_video_views/lifetime
 * - /{post-id}?fields=shares,comments.limit(0).summary(true),reactions.limit(0).summary(true)
 *
 * Use ETag via If-None-Match/ETag headers where available.
 */
@Component
public class FacebookClient {

  private final WebClient http;

  public FacebookClient(WebClient.Builder builder) {
    this.http = builder.baseUrl("https://graph.facebook.com/v23.0").build();
  }

  public Mono<String> listPublishedPosts(String pageId, String accessToken, String sinceIso, String untilIso) {
    return http.get()
        .uri(uri -> uri.path("/{pageId}/published_posts")
            .queryParam("fields", "id,created_time,permalink_url,attachments{media_type,target{id}}")
            .queryParam("limit", "100")
            .queryParam("since", sinceIso)
            .queryParam("until", untilIso)
            .queryParam("access_token", accessToken)
            .build(pageId))
        .retrieve()
        .bodyToMono(String.class);
  }
}
