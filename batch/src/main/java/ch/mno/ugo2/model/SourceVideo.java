package ch.mno.ugo2.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class SourceVideo {
  private Long id;
  private Long videoId;                 // FK to Video (nullable until reconciled)
  private String platform;              // FACEBOOK|YOUTUBE|INSTAGRAM|WORDPRESS
  private String platformSourceId;      // per-platform ID
  private String permalinkUrl;
  private String title;
  private String description;
  private String mediaType;             // VIDEO|REEL|SHORT|POST
  private boolean teaser;               // true = teaser
  private LocalDateTime publishedAt;    // UTC
  private Integer durationSeconds;      // nullable
  private String etag;                  // caching
}
