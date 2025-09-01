package ch.mno.ugo2.api;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Instagram Graph API (minimal stub).
 * - /{ig-user-id}/media?fields=id,caption,media_type,media_product_type,permalink,thumbnail_url,timestamp&limit=50
 * - /{media-id}/insights?metric=impressions,reach,video_views,plays,saves,comments,likes
 */
@Component
public class InstagramClient {
  private final WebClient http;
  public InstagramClient(WebClient.Builder builder) {
    this.http = builder.baseUrl("https://graph.facebook.com/v17.0").build();
  }
  public Mono<String> listMedia(String userId, String accessToken) {
    return http.get().uri(uri -> uri.path("/{userId}/media")
        .queryParam("fields", "id,caption,media_type,media_product_type,permalink,thumbnail_url,timestamp")
        .queryParam("limit", "50")
        .queryParam("access_token", accessToken)
        .build(userId))
      .retrieve().bodyToMono(String.class);
  }
}
