package ch.mno.ugo2.facebook;

import ch.mno.ugo2.common.AbstractClient;
import ch.mno.ugo2.facebook.dto.FbError;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

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

    public FacebookClient(WebClient facebookWebClient) {
        super(facebookWebClient);
    }

    public <T> reactor.core.publisher.Mono<T> get(FacebookQuery q, Class<T> type) {
        return get(q.getPath(), q.getParams(), type);
    }

    public <T> reactor.core.publisher.Mono<T> get(FacebookQuery q, org.springframework.core.ParameterizedTypeReference<T> type) {
        return get(q.getPath(), q.getParams(), type);
    }

    private <T> Mono<T> get(String path, Map<String, String> query, Class<T> type) {
        return http.get()
                .uri(b -> {
                    var ub = b.path(path);
                    if (query != null) {
                        query.forEach((k, v) -> {
                            if (v != null && !v.isBlank()) ub.queryParam(k, v);
                        });
                    }
                    return ub.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(body -> {
                            FbError err = safeParse(body, FbError.class);
                            var ex = new FacebookApiException(
                                    err != null ? err.message() : "Facebook API error",
                                    err != null ? err.code() : null,
                                    err != null ? err.type() : null,
                                    err != null ? err.fbtrace_id() : null,
                                    body
                            );
                            log.error("FB GET {} failed: {}", path, ex.toString());
                            return Mono.error(ex);
                        })
                )
                .bodyToMono(type)
                .retryWhen(retrySpec());
    }

    private <T> Mono<T> get(String path, Map<String, String> query, ParameterizedTypeReference<T> type) {
        return http.get()
                .uri(b -> {
                    var ub = b.path(path);
                    if (query != null) {
                        query.forEach((k, v) -> {
                            if (v != null && !v.isBlank()) ub.queryParam(k, v);
                        });
                    }
                    return ub.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(body -> {
                            FbError err = safeParse(body, FbError.class);
                            var ex = new FacebookApiException(
                                    err != null ? err.message() : "Facebook API error",
                                    err != null ? err.code() : null,
                                    err != null ? err.type() : null,
                                    err != null ? err.fbtrace_id() : null,
                                    body
                            );
                            log.error("FB GET {} failed: {}", path, ex.toString());
                            return Mono.error(ex);
                        })
                )
                .bodyToMono(type)
                .retryWhen(retrySpec());
    }


    private Retry retrySpec() {
        return Retry.backoff(3, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(15))
                .jitter(0.2)
                .filter(t ->
                        t instanceof WebClientResponseException.TooManyRequests ||
                                t instanceof IOException
                )
                .onRetryExhaustedThrow((spec, sig) -> sig.failure());
    }

}
