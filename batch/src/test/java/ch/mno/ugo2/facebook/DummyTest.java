package ch.mno.ugo2.facebook;

import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.facebook.dto.FbInsights;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@Disabled // Only manual test
class DummyTest {

    private static final String FB_VERSION = "v23.0";

    private final ch.mno.ugo2.facebook.FacebookClient client = new ch.mno.ugo2.facebook.FacebookClient();
    private static final Properties properties = new Properties();
    private static String token;

    @BeforeAll
    static void init() throws IOException {
        properties.load(DummyTest.class.getResourceAsStream("/real.properties"));
        token = properties.getProperty("facebook.token");
    }


    @Test
    void testX() {
        var videoId = "1505594320630897"; // reel

        // Appel réel
        var video = client.video("v23.0", videoId, token).block();
        var insights = client.insights("v23.0", videoId, token).block();
        System.out.println(insights);
        Map<String, Long> metricsMap = FacebookCollectorService.toFlatMap(insights);
        MetricsUpsertItem met = FacebookMetricsMapper.fromVideoAndInsights(video, metricsMap);
        System.out.println(met);
    }

}