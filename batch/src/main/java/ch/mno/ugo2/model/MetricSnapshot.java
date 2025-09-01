package ch.mno.ugo2.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class MetricSnapshot {
  private Long id;
  private Long sourceVideoId;
  private LocalDateTime snapshotAt; // UTC
  private Long views;
  private Long comments;
  private Long reactions;
  private Long shares;
  private Long saves;
}
