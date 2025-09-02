package ch.mno.ugo2.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
public class MetricsUpsertItem {
    private String platform;
    private String platform_source_id;
    private String snapshot_at;        // ISO-8601 UTC
    private Integer views_3s;
    private Integer views_platform_raw;
    private Integer comments;
    private Integer shares;
    private Integer reactions;
    private Integer saves;

    @JsonProperty("externalId")
    public String getExternalId() {
        return platform_source_id;
    }
}
