package ch.mno.ugo2.facebook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

// facebook/dto/FbVideo.java
@JsonIgnoreProperties(ignoreUnknown = true)
public record FbVideo(
  String id,
  String title,
  String description,
  @JsonProperty("permalink_url") String permalinkUrl,
  @JsonProperty("created_time") OffsetDateTime createdTime,
  @JsonProperty("length") Double lengthSeconds,
  @JsonProperty("is_crosspost") Boolean crosspost
) {}
