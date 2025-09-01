package ch.mno.ugo2.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "")
public class AppProps {

  @NotNull private Tenant tenant;
  @NotNull private Database database;
  @NotNull private Platforms platforms;
  @NotNull private Ingest ingest = new Ingest();
  @NotNull private Metrics metrics = new Metrics();
  @NotNull private Stars stars = new Stars();
  @NotNull private Alerts alerts = new Alerts();

  public Tenant getTenant() { return tenant; }
  public Database getDatabase() { return database; }
  public Platforms getPlatforms() { return platforms; }
  public Ingest getIngest() { return ingest; }
  public Metrics getMetrics() { return metrics; }
  public Stars getStars() { return stars; }
  public Alerts getAlerts() { return alerts; }

  // --- Records (Java 21)
  public record Tenant(@NotBlank String id, String displayName) {}
  public record Database(@NotBlank String url, @NotBlank String user, @NotBlank String password) {}

  public static class Platforms {
    public Facebook facebook = new Facebook();
    public YouTube youtube = new YouTube();
    public Instagram instagram = new Instagram();
    public WordPress wordpress = new WordPress();
    public Facebook getFacebook() { return facebook; }
    public YouTube getYoutube() { return youtube; }
    public Instagram getInstagram() { return instagram; }
    public WordPress getWordpress() { return wordpress; }
    public static class Facebook {
      public String apiVersion = "v23.0";
      public List<String> pageIds = List.of();
      public String accessToken = "fb-***-placeholder";
    }
    public static class YouTube {
      public List<String> channelIds = List.of();
      public String apiKey = "yt-***-placeholder";
    }
    public static class Instagram {
      public List<String> businessIds = List.of();
      public String accessToken = "ig-***-placeholder";
    }
    public static class WordPress {
      public List<String> endpoints = List.of();
    }
  }

  public static class Ingest {
    public boolean resumeCheckpoints = true;
    public boolean isResumeCheckpoints() { return resumeCheckpoints; }
  }

  public static class Metrics {
    public double minDeltaRelative = 0.01;
    public long minDeltaAbsolute = 10;
    public boolean dailyGuardSnapshot = true;
  }

  public static class Stars {
    public Thresholds thresholds = new Thresholds();
    public static class Thresholds {
      public double oneStarGradient = 0.50;
      public double twoStarsGradient = 1.50;
    }
  }

  public static class Alerts {
    public long milestoneStep = 1000;
    public List<String> emails = List.of();
    public long staleStatsHours = 24;
  }
}

