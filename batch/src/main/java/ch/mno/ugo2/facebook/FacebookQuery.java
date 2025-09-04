package ch.mno.ugo2.facebook;

import ch.mno.ugo2.config.FacebookProps;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DSL simple pour requêtes Graph API + fabriques statiques lisibles.
 */
@Getter
public final class FacebookQuery {

  private final String path;              // e.g. "/v23.0/{node}/published_posts"
  private final Map<String, String> params;

  private FacebookQuery(String path, Map<String, String> params) {
    this.path = path;
    this.params = params;
  }

  /* =========================
     Fabriques statiques
     ========================= */

  private static final String DEFAULT_FIELDS_POSTS =
          "id,created_time,permalink_url,attachments{media_type,target{id}}";

  private static final String DEFAULT_FIELDS_VIDEO =
          "id,title,description,permalink_url,created_time,length,is_crosspost";

  private static final List<String> DEFAULT_INSIGHT_METRICS = List.of(
          "total_video_views",
          "total_video_views_unique",
          "total_video_impressions",
          "total_video_10s_views"
  );

  /** Posts publiés d’une page (avec champs par défaut). */
  public static FacebookQuery buildQueryPublishedPosts(FacebookProps cfg, String pageId, String afterCursor) {
    return builder()
            .version(cfg.getApiVersion())
            .publishedPosts(pageId)
            .fields(DEFAULT_FIELDS_POSTS)
            .limit(cfg.getPageSize())
            .after(afterCursor)
            .accessToken(cfg.getAccessToken())
            .build();
  }

  /** Détails d’une vidéo (avec champs par défaut). */
  public static FacebookQuery buildQueryVideo(FacebookProps cfg, String videoId) {
    return builder()
            .version(cfg.getApiVersion())
            .video(videoId)
            .fields(DEFAULT_FIELDS_VIDEO)
            .accessToken(cfg.getAccessToken())
            .build();
  }

  /** Insights d’une vidéo (métriques par défaut). */
  public static FacebookQuery buildQueryInsights(FacebookProps cfg, String videoId) {
    return buildQueryInsights(cfg, videoId, DEFAULT_INSIGHT_METRICS);
  }

  /** Insights d’une vidéo (métriques personnalisées). */
  public static FacebookQuery buildQueryInsights(FacebookProps cfg, String videoId, List<String> metrics) {
    return builder()
            .version(cfg.getApiVersion())
            .videoInsights(videoId)
            .metrics(metrics)
            .accessToken(cfg.getAccessToken())
            .build();
  }

  /* =========================
     Builder bas-niveau
     ========================= */

  public static Builder builder() { return new Builder(); }

  public static final class Builder {
    private String version;               // "v23.0"
    private String path;                  // ex: "/{pageId}/published_posts"
    private final Map<String, String> params = new LinkedHashMap<>();

    public Builder version(String version) {
      this.version = version;
      return this;
    }

    /** /{pageId}/published_posts */
    public Builder publishedPosts(String pageId) {
      this.path = "/" + Objects.requireNonNull(pageId) + "/published_posts";
      return this;
    }

    /** /{videoId} */
    public Builder video(String videoId) {
      this.path = "/" + Objects.requireNonNull(videoId);
      return this;
    }

    /** /{videoId}/video_insights */
    public Builder videoInsights(String videoId) {
      this.path = "/" + Objects.requireNonNull(videoId) + "/video_insights";
      return this;
    }

    public Builder fields(String fields) {
      if (fields != null && !fields.isBlank()) params.put("fields", fields);
      return this;
    }

    public Builder limit(int limit) {
      if (limit > 0) params.put("limit", String.valueOf(limit));
      return this;
    }

    public Builder after(String after) {
      if (after != null && !after.isBlank()) params.put("after", after);
      return this;
    }

    public Builder metrics(List<String> metricNames) {
      if (metricNames != null && !metricNames.isEmpty()) {
        params.put("metric", String.join(",", metricNames));
      }
      return this;
    }

    public Builder param(String key, String value) {
      if (key != null && value != null && !value.isBlank()) params.put(key, value);
      return this;
    }

    public Builder accessToken(String token) { return param("access_token", token); }

    public FacebookQuery build() {
      String v = Objects.requireNonNull(this.version, "version required (e.g. v23.0)");
      String p = Objects.requireNonNull(this.path, "path required (e.g. /{id}/published_posts)");
      String fullPath = "/" + v + p;
      return new FacebookQuery(fullPath, Map.copyOf(params));
    }
  }
}
