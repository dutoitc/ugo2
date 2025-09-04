package ch.mno.ugo2.facebook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

// facebook/dto/FbPagePost.java
@JsonIgnoreProperties(ignoreUnknown = true)
public record FbPagePost(
  String id,
  @JsonProperty("created_time") OffsetDateTime createdTime,
  @JsonProperty("permalink_url") String permalinkUrl,
  Attachments attachments
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Attachments(List<Attachment> data) {}
}
