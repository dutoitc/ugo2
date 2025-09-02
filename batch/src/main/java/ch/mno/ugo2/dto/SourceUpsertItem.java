package ch.mno.ugo2.dto;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SourceUpsertItem {
    private String platform;  // FACEBOOK | YOUTUBE | INSTAGRAM | WORDPRESS
    private String platform_source_id;
    private String title;
    private String description;
    private String permalink_url;
    private String media_type;        // VIDEO | REEL | SHORT | POST
    private Integer duration_seconds; // nullable
    private String published_at;      // ISO-8601 UTC
    private Integer is_teaser;        // 0/1
    private Long video_id;            // nullable
    private Integer locked;           // 0/1

    @JsonProperty("externalId")
    public String getExternalId() {
        return platform_source_id;
    }
}
