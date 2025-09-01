package ch.mno.ugo2.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Facebook Graph v23 client: published_posts pagination.
 * Endpoints:
 * - /{page-id}/published_posts?fields=id,created_time,permalink_url,attachments{media_type,target{id}}&limit=100
 * - /{video-id}?fields=length,description,title,created_time,permalink_url
 * - /{video-id}/video_insights/total_video_views/lifetime
 * - /{post-id}?fields=shares,comments.limit(0).summary(true),reactions.limit(0).summary(true)
 */
@Component
public class FacebookClient {

  private final WebClient http;

  public FacebookClient(WebClient.Builder builder) {
    this.http = builder.baseUrl("https://graph.facebook.com/v23.0").build();
  }

  public List<Map<String,Object>> listPublishedPostsAll(String pageId, String accessToken, String sinceIso, String untilIso, int maxPages) {
    List<Map<String,Object>> items = new ArrayList<>();
    String nextUrl = "/"+pageId+"/published_posts"
        + "?fields=id,created_time,permalink_url,attachments{media_type,target{id}}"
        + "&limit=100"
        + (sinceIso!=null ? "&since="+sinceIso : "")
        + (untilIso!=null ? "&until="+untilIso : "")
        + "&access_token=" + accessToken;
    int pages = 0;
    while (nextUrl != null && pages < maxPages) {
      Map<String,Object> resp = http.get()
          .uri(nextUrl)
          .retrieve()
          .bodyToMono(new ParameterizedTypeReference<Map<String,Object>>(){})
          .block();
      if (resp == null) break;
      Object data = resp.get("data");
      if (data instanceof List<?> list) {
        for (Object o : list) {
          if (o instanceof Map) items.add((Map<String, Object>) o);
        }
      }
      // find paging.next
      String next = null;
      Object paging = resp.get("paging");
      if (paging instanceof Map<?,?> pm) {
        Object nextObj = pm.get("next");
        if (nextObj instanceof String s) next = s;
      }
      nextUrl = next;
      pages++;
    }
    return items;
  }
}
