package ch.mno.ugo2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

@Table("person")
public class Person {
  @Id
  private Long id;
  @Column("display_name")
  private String displayName;
  private String type;

  public Person() {}

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
}
