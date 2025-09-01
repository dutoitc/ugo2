package ch.mno.ugo2.api;

import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.OverrideItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class WebApiClient {

    @Getter
    private final WebClient webClient;
    private final ApiAuthSigner signer;
    private final int maxBatch;

    @Builder
    public static WebApiClient create(String baseUrl, String keyId, String secret, int maxBatch) {
        WebClient wc = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new WebApiClient(wc, new ApiAuthSigner(keyId, secret), maxBatch > 0 ? maxBatch : 1000);
    }

    public Mono<Void> health() {
        return webClient.get().uri("/api/v1/health").retrieve().bodyToMono(String.class).then();
    }

    /** Envoie un POST signé vide pour vérifier l'auth HMAC sans effet de bord. */
    public Mono<Boolean> authNoop() {
        byte[] body = Jsons.toBytes(Collections.emptyList());
        String path = "/api/v1/metrics:batchUpsert";
        return webClient.post()
                .uri(path)
                .headers(h -> {
                    signer.sign(h, "POST", path, body);
                    h.set("Idempotency-Key", UUID.randomUUID().toString());
                })
                .body(BodyInserters.fromValue(Collections.emptyList()))
                .exchangeToMono(resp -> Mono.just(resp.statusCode().is2xxSuccessful()))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)));
    }

    public Mono<Void> batchUpsertSources(List<SourceUpsertItem> items) {
        return sendChunked("/api/v1/sources:batchUpsert", items);
    }

    public Mono<Void> batchUpsertMetrics(List<MetricsUpsertItem> items) {
        return sendChunked("/api/v1/metrics:batchUpsert", items);
    }

    public Mono<Void> applyOverrides(List<OverrideItem> items) {
        return sendChunked("/api/v1/overrides:apply", items);
    }

    private <T> Mono<Void> sendChunked(String path, List<T> all) {
        if (all == null || all.isEmpty()) return Mono.empty();
        int from = 0;
        Mono<Void> chain = Mono.empty();
        while (from < all.size()) {
            int to = Math.min(from + maxBatch, all.size());
            List<T> slice = all.subList(from, to);
            chain = chain.then(send(path, slice));
            from = to;
        }
        return chain;
    }

    private <T> Mono<Void> send(String path, List<T> payload) {
        byte[] body = Jsons.toBytes(payload);
        String idempotencyKey = UUID.randomUUID().toString();
        return webClient.post()
                .uri(path)
                .headers(h -> {
                    signer.sign(h, "POST", path, body);
                    h.set("Idempotency-Key", idempotencyKey);
                })
                .body(BodyInserters.fromValue(payload))
                .exchangeToMono(resp -> handle(resp, path))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(t -> true));
    }

    private Mono<Void> handle(ClientResponse resp, String path) {
        if (resp.statusCode().is2xxSuccessful()) return Mono.empty();
        return resp.bodyToMono(String.class).defaultIfEmpty("")
                .flatMap(b -> Mono.error(new RuntimeException("API " + path + " failed: " + resp.statusCode() + " body=" + b)));
    }
}
