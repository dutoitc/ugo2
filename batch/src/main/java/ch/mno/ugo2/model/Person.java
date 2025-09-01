package ch.mno.ugo2.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Person {
  private Long id;
  private String displayName;
  private String type; // PRESENTER or DIRECTOR
}
