package ch.mno.ugo2.api;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * WordPress REST pagination (page=1..N).
 */
@Component
public class WordPressClient {
  private final WebClient http;
  public WordPressClient(WebClient.Builder builder) {
    this.http = builder.build();
  }
  public List<String> listPostsAll(String baseUrl, int maxPages) {
    List<String> pages = new ArrayList<>();
    for (int p=1; p<=maxPages; p++) {
      String url = baseUrl + "/wp-json/wp/v2/posts?per_page=50&page=" + p + "&_fields=id,date,link,title,excerpt,slug";
      String body = http.get().uri(url).retrieve().bodyToMono(String.class).block();
      if (body==null || body.trim().equals("[]")) break;
      pages.add(body);
      // WordPress returns 400 when page exceeds; this loop will break on empty array
    }
    return pages;
  }
}
