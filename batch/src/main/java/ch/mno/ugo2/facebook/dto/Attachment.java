package ch.mno.ugo2.facebook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// facebook/dto/FbAttachment.java
@JsonIgnoreProperties(ignoreUnknown = true)
public record Attachment(
        @JsonProperty("media_type") String mediaType,  // "video", "photo", ...
        Target target
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Target(String id) {}             // ← video id
}
