package ch.mno.ugo2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Table("tag")
public class Tag {
  @Id private Long id;
  private String name;
}
