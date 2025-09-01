package ch.mno.ugo2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Table("person")
public class Person {
  @Id private Long id;
  @Column("display_name") private String displayName;
  private String type;
}
