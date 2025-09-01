package ch.mno.ugo2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Table("source_video")
public class SourceVideo {
  @Id private Long id;
  @Column("video_id") private Long videoId;
  private String platform; // FACEBOOK|YOUTUBE|INSTAGRAM|WORDPRESS|TIKTOK
  @Column("platform_source_id") private String platformSourceId;
  @Column("permalink_url") private String permalinkUrl;
  private String title;
  private String description;
  @Column("media_type") private String mediaType; // VIDEO|REEL|SHORT
  @Column("is_teaser") private boolean teaser;
  @Column("published_at") private LocalDateTime publishedAt;
  @Column("duration_seconds") private Integer durationSeconds;
  private String etag;
}
