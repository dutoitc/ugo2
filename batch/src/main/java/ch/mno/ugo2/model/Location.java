package ch.mno.ugo2.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Location {
  private Long id;
  private String label;
  private double latitude;
  private double longitude;
}
