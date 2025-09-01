package ch.mno.ugo2.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

@Table("video")
public class Video {
  @Id
  private Long id;
  @Column("canonical_title")
  private String canonicalTitle;
  @Column("canonical_description")
  private String canonicalDescription;
  @Column("official_published_at")
  private LocalDateTime officialPublishedAt;
  @Column("location_id")
  private Long locationId;

  public Video() {}

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getCanonicalTitle() { return canonicalTitle; }
  public void setCanonicalTitle(String canonicalTitle) { this.canonicalTitle = canonicalTitle; }

  public String getCanonicalDescription() { return canonicalDescription; }
  public void setCanonicalDescription(String canonicalDescription) { this.canonicalDescription = canonicalDescription; }

  public LocalDateTime getOfficialPublishedAt() { return officialPublishedAt; }
  public void setOfficialPublishedAt(LocalDateTime officialPublishedAt) { this.officialPublishedAt = officialPublishedAt; }

  public Long getLocationId() { return locationId; }
  public void setLocationId(Long locationId) { this.locationId = locationId; }
}
