package ch.mno.ugo2.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class Video {
  private Long id;
  private String canonicalTitle;
  private String canonicalDescription;
  private LocalDateTime officialPublishedAt;
  private Long locationId; // FK to Location (nullable)
}
