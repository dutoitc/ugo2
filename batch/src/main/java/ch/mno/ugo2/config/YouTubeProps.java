package ch.mno.ugo2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "ugo2.youtube")
@Data
public class YouTubeProps {
    /** API key for YouTube Data API v3 */
    private String apiKey;
    /** Channel IDs to scan */
    private List<String> channelIds = new ArrayList<>();
    /** Rolling window in days for batch:run */
    private int windowDaysRolling = 7;
    /** Page size for playlistItems */
    private int pageSize = 50;
    /** Max videos per execution (safety) */
    private int maxVideosPerRun = 500;
    /** Local state dir for checkpoints/caches */
    private String stateDir = ".ugo2";
    /** Thresholds to reduce chatter */
    private double minDeltaRel = 0.01; // 1%
    private int minDeltaAbs = 10;      // 10 views
    /** Ensure at least one snapshot/day even if below delta */
    private boolean dailyFloor = true;
}
