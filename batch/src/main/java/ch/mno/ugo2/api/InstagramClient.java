package ch.mno.ugo2.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Instagram Graph API (v23.0) pagination for media list.
 */
@Component
public class InstagramClient {
  private final WebClient http;
  public InstagramClient(WebClient.Builder builder) {
    this.http = builder.baseUrl("https://graph.facebook.com/v23.0").build();
  }
  public List<Map<String,Object>> listMediaAll(String userId, String accessToken, int maxPages) {
    List<Map<String,Object>> items = new ArrayList<>();
    String nextUrl = "/"+userId+"/media?fields=id,caption,media_type,media_product_type,permalink,thumbnail_url,timestamp&limit=50&access_token="+accessToken;
    int pages = 0;
    while (nextUrl != null && pages < maxPages) {
      Map<String,Object> resp = http.get().uri(nextUrl).retrieve()
          .bodyToMono(new ParameterizedTypeReference<Map<String,Object>>(){})
          .block();
      if (resp == null) break;
      Object data = resp.get("data");
      if (data instanceof List<?> list) for (Object o: list) if (o instanceof Map) items.add((Map<String,Object>) o);
      String next = null;
      Object paging = resp.get("paging");
      if (paging instanceof Map<?,?> pm) {
        Object n = pm.get("next");
        if (n instanceof String s) next = s;
      }
      nextUrl = next;
      pages++;
    }
    return items;
  }
}
