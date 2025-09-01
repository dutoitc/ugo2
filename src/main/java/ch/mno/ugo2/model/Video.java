package ch.mno.ugo2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Table("video")
public class Video { 
  @Id private Long id;
  @Column("canonical_title") private String canonicalTitle;
  @Column("canonical_description") private String canonicalDescription;
  @Column("official_published_at") private LocalDateTime officialPublishedAt;
  @Column("location_id") private Long locationId;
}
