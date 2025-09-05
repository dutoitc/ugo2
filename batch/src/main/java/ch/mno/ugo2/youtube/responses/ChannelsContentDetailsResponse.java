package ch.mno.ugo2.youtube.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class ChannelsContentDetailsResponse {
    private String kind;
    private String etag;
    private PageInfo pageInfo;
    private List<ChannelItem> items;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class PageInfo {
        private int totalResults;
        private int resultsPerPage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class ChannelItem {
        private String kind;
        private String etag;
        private String id;

        @JsonProperty("contentDetails")
        private ContentDetails contentDetails;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class ContentDetails {
        @JsonProperty("relatedPlaylists")
        private RelatedPlaylists relatedPlaylists;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class RelatedPlaylists {
        // "likes" peut être vide dans ton exemple → String nullable
        private String likes;
        private String uploads;
    }
}
