package ch.mno.ugo2.api;

import ch.mno.ugo2.facebook.FacebookApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacebookClient {

    private final WebClient http;
    private final ObjectMapper om = new ObjectMapper();

    public Map<String, Object> get(String path, String accessToken, String fields) {
        return http.get()
                .uri(b -> {
                    var ub = b.path(path).queryParam("access_token", sanitize(accessToken));
                    if (fields != null && !fields.isBlank()) ub.queryParam("fields", fields);
                    return ub.build();
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).defaultIfEmpty("")
                                .map(body -> buildFbException("GET " + path, body)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }


    public List<Map<String, Object>> listPublishedPosts(String pageId, String fields, int limit, String accessToken) {
        List<Map<String, Object>> items = new ArrayList<>();

        // 1) première page via UriBuilder (laisse WebClient encoder proprement)
        String firstUrl = http.get()
                .uri(b -> b
                        .pathSegment(pageId, "published_posts")
                        .queryParam("limit", limit)
                        .queryParam("access_token", sanitize(accessToken))
                        .queryParam("fields", fields)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class).defaultIfEmpty("")
                        .map(body -> buildFbException("GET /" + pageId + "/published_posts", body)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(resp -> {
                    appendData(resp, items);
                    return extractNext(resp); // retourne l'URL absolue de paging.next ou null
                })
                .block();

        // 2) pagination: passer l’URL absolue telle quelle, via URI.create(...) pour éviter le re-encodage
        String nextUrl = firstUrl;
        int pages = 1;
        while (nextUrl != null) {
            final String url = nextUrl; // effectively final pour le lambda
            Map<String, Object> resp = http.get()
                    .uri(URI.create(url)) // <== IMPORTANT: pas de double-encodage
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class).defaultIfEmpty("")
                            .map(body -> buildFbException("GET " + url, body)))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (resp == null) break;
            appendData(resp, items);
            nextUrl = extractNext(resp);

            if (++pages > 100) {
                log.warn("FB paging: stop after {} pages for {}", pages, pageId);
                break;
            }
        }
        return items;
    }



// ---- helpers pagination (ajoute dans la même classe)

    @SuppressWarnings("unchecked")
    private static void appendData(Map<String, Object> resp, List<Map<String, Object>> out) {
        Object data = resp.get("data");
        if (data instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map) out.add((Map<String, Object>) o);
            }
        }
    }

    private static String extractNext(Map<String, Object> resp) {
        Object paging = resp.get("paging");
        if (paging instanceof Map<?, ?> pm) {
            Object n = pm.get("next");
            if (n instanceof String s && !s.isBlank()) {
                return s; // URL absolue — on la garde telle quelle
            }
        }
        return null;
    }


    // ---------- helpers

    private String buildUri(String path, String accessToken, String fields) {
        return path
                + "?access_token=" + sanitize(accessToken)
                + (fields != null && !fields.isBlank() ? "&fields=" + fields : "");
    }

    private static String sanitize(String s) {
        return s == null ? null : s.replace("\"", "").trim();
    }

    private FacebookApiException buildFbException(String where, String body) {
        // Essaie d’extraire un message clair
        try {
            JsonNode root = om.readTree(body);
            JsonNode err = root.path("error");
            String msg = optText(err, "message");
            String type = optText(err, "type");
            Integer code = err.has("code") ? err.get("code").asInt() : null;
            String trace = optText(err, "fbtrace_id");

            String pretty = (msg != null ? msg : "Facebook API error")
                    + (type != null ? " [type=" + type + "]" : "")
                    + (code != null ? " [code=" + code + "]" : "")
                    + (trace != null ? " [fbtrace_id=" + trace + "]" : "");

            log.error("FB call failed at {}: {}", where, pretty);
            return new FacebookApiException(pretty, code, type, trace, body);
        } catch (Exception parseFail) {
            // Si pas JSON, renvoyer le corps brut
            String pretty = "Facebook API error (non-JSON): " + abbreviate(body, 500);
            log.error("FB call failed at {}: {}", where, pretty);
            return new FacebookApiException(pretty, null, null, null, body);
        }
    }

    private static String optText(JsonNode node, String field) {
        return (node != null && node.has(field) && !node.get(field).isNull()) ? node.get(field).asText() : null;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
