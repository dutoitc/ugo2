package ch.mno.ugo2.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InstagramClientTest {

    private final ObjectMapper om = new ObjectMapper();


    static class FakeIgClient extends InstagramClient {
        private final Map<String, Map<String, Object>> pages = new HashMap<>();

        FakeIgClient() {
            super();
        }

        void addPage(String url, List<Map<String, Object>> data, String nextUrl) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("data", data);
            if (nextUrl != null) {
                Map<String, Object> paging = new HashMap<>();
                paging.put("next", nextUrl);
                resp.put("paging", paging);
            }
            pages.put(url, resp);
        }

        @Override
        protected Map<String, Object> fetchPage(String url) {
            return pages.get(url);
        }
    }

    @Test
    void listMedia_multiPages_collectsAll_whenNoMaxPages() {
        FakeIgClient client = new FakeIgClient();

        Map<String, Object> m1 = new HashMap<>();
        m1.put("id", "A1");
        Map<String, Object> m2 = new HashMap<>();
        m2.put("id", "A2");
        client.addPage("u1", List.of(m1, m2), "u2");

        Map<String, Object> m3 = new HashMap<>();
        m3.put("id", "B1");
        client.addPage("u2", List.of(m3), null);

        List<Map<String, Object>> out = client.listMedia("u1", null);
        assertNotNull(out);
        assertEquals(3, out.size());
        assertEquals("A1", out.get(0).get("id"));
        assertEquals("A2", out.get(1).get("id"));
        assertEquals("B1", out.get(2).get("id"));
    }

    @Test
    void listMedia_respectsMaxPages() {
        FakeIgClient client = new FakeIgClient();

        Map<String, Object> m1 = new HashMap<>();
        m1.put("id", "A1");
        client.addPage("u1", List.of(m1), "u2");

        Map<String, Object> m2 = new HashMap<>();
        m2.put("id", "B1");
        client.addPage("u2", List.of(m2), "u3");

        Map<String, Object> m3 = new HashMap<>();
        m3.put("id", "C1");
        client.addPage("u3", List.of(m3), null);

        // maxPages = 2 -> ne lit que u1 et u2
        List<Map<String, Object>> out = client.listMedia("u1", 2);
        assertNotNull(out);
        assertEquals(2, out.size());
        assertEquals("A1", out.get(0).get("id"));
        assertEquals("B1", out.get(1).get("id"));
    }

    @Test
    void listMedia_handlesNullResponse() {
        FakeIgClient client = new FakeIgClient();

        // aucune page enregistrée -> fetchPage("u1") == null
        List<Map<String, Object>> out = client.listMedia("u1", 10);
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    void listMedia_ignoresNonListData() {
        FakeIgClient client = new FakeIgClient();

        Map<String, Object> resp = new HashMap<>();
        resp.put("data", "not-a-list");
        client.pages.put("u1", resp);

        List<Map<String, Object>> out = client.listMedia("u1", 1);
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }


    @Test
    void mapInsights() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/instagram-insights.json")) {
            assertNotNull(is, "resource not found: instagram-insights.json");
            Map<String, Object> data = om.readValue(is, new TypeReference<>() {
            });
            Map<String, Long> ret = InstagramClient.mapInsights(data);

            assertEquals(7, ret.size(), "expected 7 metrics");
            assertEquals(248L, ret.get("views"));
            assertEquals(150L, ret.get("reach"));
            assertEquals(1L, ret.get("saved"));
            assertEquals(7L, ret.get("likes"));
            assertEquals(0L, ret.get("comments"));
            assertEquals(4L, ret.get("shares"));
            assertEquals(12L, ret.get("total_interactions"));
        }
    }
}
