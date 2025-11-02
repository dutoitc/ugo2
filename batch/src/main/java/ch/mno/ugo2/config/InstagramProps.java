package ch.mno.ugo2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "ugo2.instagram")
@Data
public class InstagramProps {
  /** Graph API version, e.g. "v23.0" */
  private String apiVersion = "v23.0";
  /** Instagram Business Account IDs to scan (as strings) */
  private List<String> userIds = new ArrayList<>();
  /** Long-lived access token with required scopes */
  private String accessToken;

  /** Initial full scan window (days back) for batch:init */
  private int windowDaysInitial = 365*3;
  /** Rolling window (days back) for batch:run */
  private int windowDaysRolling = 7;

  /** Pagination page size (max ~100) */
  private int pageSize = 100;
  /** Hard limits to avoid surcharges */
  private int maxMediaPerRun = 500;

  /** Optional local state dir for future cache/checkpoints */
  private String stateDir = ".ugo2";
}
