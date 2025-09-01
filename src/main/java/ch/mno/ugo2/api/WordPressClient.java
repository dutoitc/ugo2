package ch.mno.ugo2.api;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WordPress REST (minimal stub).
 * - wp-json/wp/v2/posts?per_page=50&_fields=id,date,link,title,excerpt,slug
 */
@Component
public class WordPressClient {
  private final WebClient http;
  public WordPressClient(WebClient.Builder builder) {
    this.http = builder.build(); // baseUrl supplied per call
  }
  public Mono<String> listPosts(String baseUrl) {
    return http.get().uri(baseUrl + "/wp-json/wp/v2/posts?per_page=50&_fields=id,date,link,title,excerpt,slug")
      .retrieve().bodyToMono(String.class);
  }
}
