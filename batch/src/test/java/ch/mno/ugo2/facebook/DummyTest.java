package ch.mno.ugo2.facebook;

import ch.mno.ugo2.facebook.dto.FbInsights;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@Disabled // Only manual test
class DummyTest {

    private static final String FB_VERSION = "v23.0";

    private final ObjectMapper om = new ObjectMapper().findAndRegisterModules();
    private final WebClient fbWebClient = WebClient.builder().baseUrl("https://graph.facebook.com").build();
    private final ch.mno.ugo2.facebook.FacebookClient client = new ch.mno.ugo2.facebook.FacebookClient(fbWebClient, om);
    private static final Properties properties = new Properties();
    private static String token;

    @BeforeAll
    static void init() throws IOException {
        properties.load(DummyTest.class.getResourceAsStream("/real.properties"));
        token = properties.getProperty("facebook.token");
    }


    @Test
    void testX() {
        var videoId = "775787271602561";
        List<String> metrics = List.of("total_video_views", "total_video_views_unique", "total_video_impressions", "total_video_10s_views");
        var q = FacebookQuery.builder().version(FB_VERSION).videoInsights(videoId).metrics(metrics).accessToken(token).build();

        // Appel réel
        FbInsights insights = assertDoesNotThrow(() -> client.get(q, FbInsights.class).block(Duration.ofSeconds(30)), "L'appel Graph API ne doit pas lever d'exception (vérifiez token/permissions/ID)");
        System.out.println(insights);
    }

}
