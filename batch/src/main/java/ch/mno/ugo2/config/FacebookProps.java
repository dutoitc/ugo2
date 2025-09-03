package ch.mno.ugo2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "ugo2.facebook")
@Data
public class FacebookProps {
  /** Graph API version, e.g. "v23.0" */
  private String apiVersion = "v23.0";
  /** Page access token (long-lived) */
  private String accessToken;
  /** One or more Facebook Page IDs to scan */
  private List<String> pageIds = new ArrayList<>();

  /** Initial full scan window (days back) for batch:init */
  private int windowDaysInitial = 365*7;
  /** Rolling window (days back) for batch:run */
  private int windowDaysRolling = 7;

  /** Pagination page size (max 100) */
  private int pageSize = 100;
  /** Hard limits to avoid surcharges */
  private int maxPostsPerPage = 100;
  private int maxVideosPerRun = 500;

  /** Local dir pour checkpoints/ETags si on ajoute du cache */
  private String stateDir = ".ugo2";
}
