package ch.mno.ugo2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

@Table("source_video")
public class SourceVideo {
  @Id
  private Long id;
  @Column("video_id")
  private Long videoId;
  private String platform;
  @Column("platform_source_id")
  private String platformSourceId;
  @Column("permalink_url")
  private String permalinkUrl;
  private String title;
  private String description;
  @Column("media_type")
  private String mediaType;
  @Column("is_teaser")
  private boolean teaser;
  @Column("published_at")
  private LocalDateTime publishedAt;
  @Column("duration_seconds")
  private Integer durationSeconds;
  private String etag;

  public SourceVideo() {}

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public Long getVideoId() { return videoId; }
  public void setVideoId(Long videoId) { this.videoId = videoId; }

  public String getPlatform() { return platform; }
  public void setPlatform(String platform) { this.platform = platform; }

  public String getPlatformSourceId() { return platformSourceId; }
  public void setPlatformSourceId(String platformSourceId) { this.platformSourceId = platformSourceId; }

  public String getPermalinkUrl() { return permalinkUrl; }
  public void setPermalinkUrl(String permalinkUrl) { this.permalinkUrl = permalinkUrl; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }

  public String getMediaType() { return mediaType; }
  public void setMediaType(String mediaType) { this.mediaType = mediaType; }

  public boolean isTeaser() { return teaser; }
  public void setTeaser(boolean teaser) { this.teaser = teaser; }

  public LocalDateTime getPublishedAt() { return publishedAt; }
  public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

  public Integer getDurationSeconds() { return durationSeconds; }
  public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

  public String getEtag() { return etag; }
  public void setEtag(String etag) { this.etag = etag; }
}
