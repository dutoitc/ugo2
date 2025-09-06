package ch.mno.ugo2.facebook.responses;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO propre pour l’objet Graph "Video".
*/
// https://developers.facebook.com/tools/explorer/?method=GET&path=775787271602561%3Ffields%3Did%2Cpermalink_url%2Ccreated_time%2Clength%2Cpost_views%2Clikes%2Ctitle%2Cdescription%2Cviews%2Cpublished%2Cscheduled_publish_time&version=v23.0
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