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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;

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
        return webClient.get().uri("/api/v1/health")
                .retrieve()
                .bodyToMono(String.class)
                .then();
    }

    /**
     * POST vide signé pour valider l’auth, sans effet de bord.
     */
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
        return sendChunkedWrapped("/api/v1/sources:batchUpsert", "sources", items);
    }

    public Mono<Void> batchUpsertMetrics(List<MetricsUpsertItem> items) {
        return sendChunkedWrapped("/api/v1/metrics:batchUpsert", "snapshots", items);
    }

    public Mono<Void> applyOverrides(List<OverrideItem> items) {
        return sendChunked("/api/v1/overrides:apply", items);
    }

    /**
     * Retourne la liste des IDs manquants pour une plateforme donnée.
     */
    public Mono<List<String>> filterMissingSources(String platform, List<String> ids) {
        if (ids == null || ids.isEmpty()) return Mono.just(List.of());
        final String path = "/api/v1/sources:filterMissing";

        Map<String, Object> req = new HashMap<>();
        req.put("platform", platform);
        req.put("ids", ids);

        byte[] body = Jsons.toBytes(req);
        log.info("API POST {} (check ids={})", path, ids.size());

        return webClient.post()
                .uri(path)
                .headers(h -> {
                    signer.sign(h, "POST", path, body);
                    h.set("Idempotency-Key", UUID.randomUUID().toString());
                })
                .body(BodyInserters.fromValue(req))
                .retrieve()
                .bodyToMono(FilterMissingResp.class)
                .map(resp -> {
                    if (resp == null || resp.missing == null) return List.<String>of();
                    // Force List<String> propre, même si le JSON était non typé
                    List<String> out = new ArrayList<>(resp.missing.size());
                    for (Object o : resp.missing) out.add(String.valueOf(o));
                    return out;
                })
                .doOnError(WebClientResponseException.class, e ->
                        log.warn("API {} -> {} body={}", path, e.getRawStatusCode(), e.getResponseBodyAsString()));
    }

    /**
     * DTO JSON pour /sources:filterMissing
     */
    public static class FilterMissingResp {
        public boolean ok;
        public String platform;
        public Integer requested;
        public Integer known;
        public List<String> missing; // bien typé
    }

    // -------- internals --------

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

    private <T> Mono<Void> send(String path, Object payload) {
        byte[] body = Jsons.toBytes(payload);
        String idempotencyKey = UUID.randomUUID().toString();
        int items = (payload instanceof List<?> l) ? l.size() :
                (payload instanceof Map<?, ?> m && m.values().stream().findFirst().orElse(null) instanceof List<?> l2 ? ((List<?>) m.values().stream().findFirst().get()).size() : -1);
        log.info("API POST {} (items={}, bytes={})", path, items, body.length);

        return webClient.post()
                .uri(path)
                .headers(h -> {
                    signer.sign(h, "POST", path, body);
                    h.set("Idempotency-Key", idempotencyKey);
                })
                .body(BodyInserters.fromValue(payload))
                .exchangeToMono(resp -> handle(resp, path))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)));
    }

    private Mono<Void> handle(ClientResponse resp, String path) {
        int code = resp.statusCode().value();
        if (resp.statusCode().is2xxSuccessful()) {
            log.info("API {} -> {}", path, code);
            return resp.bodyToMono(Void.class);
        }
        return resp.bodyToMono(String.class).defaultIfEmpty("")
                .flatMap(b -> {
                    log.warn("API {} -> {} body={}", path, code, b);
                    return Mono.error(new RuntimeException("API " + path + " failed: " + code));
                });
    }


    /**
     * Envoi chunké avec enveloppe {"wrapperKey":[slice]} pour respecter le schéma PHP.
     */
    private <T> Mono<Void> sendChunkedWrapped(String path, String wrapperKey, List<T> all) {
        if (all == null || all.isEmpty()) return Mono.empty();
        int from = 0;
        Mono<Void> chain = Mono.empty();
        while (from < all.size()) {
            int to = Math.min(from + maxBatch, all.size());
            List<T> slice = all.subList(from, to);
            Map<String, Object> wrapped = Map.of(wrapperKey, slice);
            chain = chain.then(send(path, wrapped));
            from = to;
        }
        return chain;
    }

    public Mono<Void> runReconcile(String fromIso, String toIso, int hoursWindow, boolean dryRun) {
        Map<String,Object> body = Map.of(
                "from", fromIso, "to", toIso,
                "hoursWindow", hoursWindow, "dryRun", dryRun
        );
        return send("/api/v1/reconcile:run", body);
    }


}
