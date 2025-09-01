package ch.mno.ugo2.api;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * YouTube Data API v3 (minimal stub).
 * - search.list?channelId={id}&type=video&order=date&publishedAfter={iso}&maxResults=50&fields=items(id(videoId),snippet(publishedAt,title,description))
 * - videos.list?part=snippet,contentDetails,statistics&id={ids}&fields=items(id,etag,snippet(publishedAt,title,description,channelId),contentDetails(duration),statistics(viewCount,likeCount,commentCount))
 */
@Component
public class YouTubeClient {
  private final WebClient http;
  public YouTubeClient(WebClient.Builder builder) {
    this.http = builder.baseUrl("https://www.googleapis.com/youtube/v3").build();
  }
  public Mono<String> search(String channelId, String apiKey, String publishedAfterIso) {
    return http.get().uri(uri -> uri.path("/search")
        .queryParam("channelId", channelId)
        .queryParam("type", "video")
        .queryParam("order", "date")
        .queryParam("publishedAfter", publishedAfterIso)
        .queryParam("maxResults", "50")
        .queryParam("fields", "items(id(videoId),snippet(publishedAt,title,description))")
        .queryParam("key", apiKey).build())
      .retrieve().bodyToMono(String.class);
  }
}
