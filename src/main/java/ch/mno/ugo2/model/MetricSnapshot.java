package ch.mno.ugo2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

@Table("metric_snapshot")
public class MetricSnapshot {
  @Id
  private Long id;
  @Column("source_video_id")
  private Long sourceVideoId;
  @Column("snapshot_at")
  private LocalDateTime snapshotAt;
  private Long views;
  private Long comments;
  private Long reactions;
  private Long shares;
  private Long saves;

  public MetricSnapshot() {}

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public Long getSourceVideoId() { return sourceVideoId; }
  public void setSourceVideoId(Long sourceVideoId) { this.sourceVideoId = sourceVideoId; }

  public LocalDateTime getSnapshotAt() { return snapshotAt; }
  public void setSnapshotAt(LocalDateTime snapshotAt) { this.snapshotAt = snapshotAt; }

  public Long getViews() { return views; }
  public void setViews(Long views) { this.views = views; }

  public Long getComments() { return comments; }
  public void setComments(Long comments) { this.comments = comments; }

  public Long getReactions() { return reactions; }
  public void setReactions(Long reactions) { this.reactions = reactions; }

  public Long getShares() { return shares; }
  public void setShares(Long shares) { this.shares = shares; }

  public Long getSaves() { return saves; }
  public void setSaves(Long saves) { this.saves = saves; }
}
