package ch.mno.ugo2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("location")
public class Location {
  @Id
  private Long id;
  private String label;
  private double latitude;
  private double longitude;

  public Location() {}

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getLabel() { return label; }
  public void setLabel(String label) { this.label = label; }

  public double getLatitude() { return latitude; }
  public void setLatitude(double latitude) { this.latitude = latitude; }

  public double getLongitude() { return longitude; }
  public void setLongitude(double longitude) { this.longitude = longitude; }
}
