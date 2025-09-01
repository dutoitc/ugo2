package ch.mno.ugo2.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * YouTube Data API v3 (pagination via nextPageToken).
 * - search.list?channelId={id}&type=video&order=date&publishedAfter={iso}&maxResults=50
 *   &fields=nextPageToken,items(id(videoId),snippet(publishedAt,title,description))
 */
@Component
public class YouTubeClient {
  private final WebClient http;

  public YouTubeClient(WebClient.Builder builder) {
    this.http = builder.baseUrl("https://www.googleapis.com/youtube/v3").build();
  }

  public List<Map<String, Object>> searchAll(String channelId, String apiKey, String publishedAfterIso, int maxPages) {
    List<Map<String, Object>> items = new ArrayList<>();
    String pageToken = null;
    int pages = 0;

    while (pages < maxPages) {
      // capture de pageToken dans une variable locale "effectively final" pour la lambda
      final String pt = pageToken;

      Map<String, Object> resp = http.get()
              .uri(uri -> {
                var b = uri.path("/search")
                        .queryParam("channelId", channelId)
                        .queryParam("type", "video")
                        .queryParam("order", "date")
                        .queryParam("publishedAfter", publishedAfterIso)
                        .queryParam("maxResults", "50")
                        .queryParam("fields", "nextPageToken,items(id(videoId),snippet(publishedAt,title,description))")
                        .queryParam("key", apiKey);
                if (pt != null && !pt.isEmpty()) {
                  b = b.queryParam("pageToken", pt);
                }
                return b.build();
              })
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .block();

      if (resp == null) break;

      Object data = resp.get("items");
      if (data instanceof List<?> list) {
        for (Object o : list) {
          if (o instanceof Map) items.add((Map<String, Object>) o);
        }
      }

      Object next = resp.get("nextPageToken");
      if (next instanceof String s && !s.isEmpty()) {
        pageToken = s;
        pages++;
      } else {
        break;
      }
    }

    return items;
  }
}
