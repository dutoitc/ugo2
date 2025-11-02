package ch.mno.ugo2.api;

import ch.mno.ugo2.common.AbstractClient;
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
public class InstagramClient  extends AbstractClient {

    public InstagramClient() {
        super(WebClient.builder().build());
    }

    /**
     * Liste les médias IG pour un user (URL complète déjà paramétrée).
     * Suit les liens 'paging.next' et renvoie une liste plate de maps.
     *
     * @param url première page (contient fields, limit, access_token)
     * @param maxPages limite dure ; null ou <=0 pour "illimité"
     */
    public List<Map<String,Object>> listMedia(String url, Integer maxPages) {
        List<Map<String,Object>> items = new ArrayList<>();
        Pager p = new Pager(url, maxPages);

        while (p.canContinue()) {
            Map<String,Object> resp = fetchPage(p.url());
            if (resp == null) {
                p.onNullResponse();
                continue;
            }
            items.addAll(readData(resp.get("data")));
            p.advanceFrom(resp);
        }
        return items;
    }

    private static List<Map<String, Object>> readData(Object data) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (data instanceof List<?> list) {
            for (Object o: list) {
                if (o instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String,Object> m = (Map<String,Object>) o;
                    items.add(m);
                }
            }
        }
        return items;
    }

    /**
     * Point d’extension pour les tests : lecture d’une page (GET + decode).
     * Extraction minimale de l’appel http initialement inline (aucun autre changement).
     */
    protected Map<String,Object> fetchPage(String url) {
        return http.get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String,Object>>() {})
                .block();
    }

    /**
     * Lit les insights d'un média (URL complète déjà paramétrée).
     * Retourne un map name->value (première valeur).
     */
    public Map<String, Long> readInsights(String url) {
        Map<String,Object> resp = http.get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String,Object>>() {})
                .block();
        if (resp == null) return java.util.Collections.emptyMap();
        return mapInsights(resp);
    }

    static Map<String, Long> mapInsights(Map<String, Object> resp) {
        Map<String, Long> out = new java.util.HashMap<>();
        Object data = resp.get("data");
        if (data instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?,?> m) {
                    Object name = m.get("name");
                    String n = name == null ? null : String.valueOf(name);
                    Object values = m.get("values");
                    if (values instanceof List<?> vlist && !vlist.isEmpty()) {
                        Object first = vlist.get(0);
                        if (first instanceof Map<?,?> vm) {
                            Object val = vm.get("value");
                            if (n != null && val != null) {
                                try {
                                    out.put(n, Long.valueOf(String.valueOf(val)));
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                }
            }
        }
        return out;
    }


    private static final class Pager {
        private String nextUrl;
        private int pages;
        private final int maxPages; // <=0 = illimité

        Pager(String startUrl, Integer maxPages) {
            this.nextUrl = startUrl;
            this.maxPages = (maxPages == null ? 0 : maxPages);
            this.pages = 0;
        }

        boolean canContinue() {
            return nextUrl != null && !nextUrl.isEmpty() && (maxPages <= 0 || pages < maxPages);
        }

        String url() { return nextUrl; }

        void onNullResponse() { this.nextUrl = null; }

        void advanceFrom(Map<String,Object> resp) {
            String next = null;
            Object paging = resp.get("paging");
            if (paging instanceof Map<?,?> pm) {
                Object n = pm.get("next");
                if (n instanceof String s) next = s;
            }
            this.nextUrl = next;
            this.pages++;
        }
    }
}
