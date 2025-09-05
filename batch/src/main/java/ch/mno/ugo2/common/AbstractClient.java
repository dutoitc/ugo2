package ch.mno.ugo2.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
public class AbstractClient {

    protected final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    protected final WebClient http;

    protected JsonNode toJsonNode(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * GET JSON avec prise en charge du 304:
     * - on tente avec If-None-Match si etag != null
     * - si 304 Not Modified, on refait la requête SANS ETag pour récupérer le corps
     */
    protected Mono<JsonNode> getJson(URI uri, String etag) {
        String safeUri = uri.toString().replaceAll("([?&]key=)[^&]+", "$1***");
        log.debug("GET {}", safeUri);

        return http.get().uri(uri).headers(h -> {
            if (etag != null) h.set(HttpHeaders.IF_NONE_MATCH, etag);
        }).exchangeToMono(resp -> {
            int code = resp.statusCode().value();

            // 2xx -> lire le corps
            if (resp.statusCode().is2xxSuccessful()) {
                return resp.bodyToMono(String.class).map(this::toJsonNode);
            }

            // 304 -> fallback sans ETag
            if (code == HttpStatus.NOT_MODIFIED.value()) {
                log.debug("HTTP 304 Not Modified — fallback GET without ETag");
                return http.get().uri(uri).retrieve().bodyToMono(String.class).map(this::toJsonNode);
            }

            // autres erreurs -> message lisible
            return resp.bodyToMono(String.class).defaultIfEmpty("").flatMap(b -> Mono.error(new RuntimeException("API error " + code + " body=" + b)));
        }).timeout(Duration.ofSeconds(20));
    }


    /**
     * GET générique avec prise en charge du 304:
     * - tente avec If-None-Match si etag != null
     * - si 304 Not Modified, refait la requête sans ETag pour récupérer le corps
     */
    protected <T> Mono<T> get(URI uri, String etag, Class<T> clazz) {
        String safeUri = uri.toString().replaceAll("([?&]key=)[^&]+", "$1***");
        log.debug("GET {}", safeUri);

        return http.get()
                .uri(uri)
                .headers(h -> {
                    if (etag != null) h.set(HttpHeaders.IF_NONE_MATCH, etag);
                })
                .exchangeToMono(resp -> {
                    int code = resp.statusCode().value();

                    // 2xx -> lire le corps en clazz
                    if (resp.statusCode().is2xxSuccessful()) {
                        return resp.bodyToMono(clazz);
                    }

                    // 304 -> fallback sans ETag
                    if (code == HttpStatus.NOT_MODIFIED.value()) {
                        log.debug("HTTP 304 Not Modified — fallback GET without ETag");
                        return http.get()
                                .uri(uri)
                                .retrieve()
                                .bodyToMono(clazz);
                    }

                    // autres erreurs -> message lisible
                    return resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(b -> Mono.error(new RuntimeException(
                                    "API error " + code + " body=" + b)));
                })
                .timeout(Duration.ofSeconds(20));
    }


    protected <T> T safeParse(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            return null;
        }
    }

}