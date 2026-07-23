package ch.mno.ugo2.facebook.responses;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO propre pour l’objet Graph "Video".
*/
// Example: query video 1000000000000000 with the fields declared by FacebookClient.
@JsonIgnoreProperties(ignoreUnknown = true)
public record VideoResponse(
        String id,
        String title,

        String description,

        @JsonProperty("permalink_url")
        String permalinkUrl,

        @JsonProperty("created_time")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
        java.time.Instant createdTime,

        @JsonProperty("length")
        Double lengthSeconds

) {
        public Integer durationSecondsOrNull() {
                return lengthSeconds == null ? null : (int)Math.round(lengthSeconds);
        }
}
