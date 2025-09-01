package ch.mno.ugo2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Table("metric_snapshot")
public class MetricSnapshot {
  @Id private Long id;
  @Column("source_video_id") private Long sourceVideoId;
  @Column("snapshot_at") private LocalDateTime snapshotAt;
  private Long views;
  private Long comments;
  private Long reactions;
  private Long shares;
  private Long saves;
}
