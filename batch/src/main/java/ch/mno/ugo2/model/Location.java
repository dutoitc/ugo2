package ch.mno.ugo2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Table("location")
public class Location {
  @Id private Long id;
  private String label;
  private double latitude;
  private double longitude;
}
