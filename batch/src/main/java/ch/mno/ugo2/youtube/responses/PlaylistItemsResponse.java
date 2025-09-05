package ch.mno.ugo2.youtube.responses;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlaylistItemsResponse {
    private String kind;
    private String etag;
    private String nextPageToken;
    private List<Item> items;
    private PageInfo pageInfo;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String kind;
        private String etag;
        private String id;
        private Snippet snippet;
        private ContentDetails contentDetails;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Snippet {
        private Instant publishedAt;
        private String channelId;
        private String title;
        private String description;
        private Thumbnails thumbnails;
        private String channelTitle;
        private String playlistId;
        private Integer position;
        private ResourceId resourceId;
        private String videoOwnerChannelTitle;
        private String videoOwnerChannelId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceId {
        private String kind;
        private String videoId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentDetails {
        private String videoId;
        private Instant videoPublishedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Thumbnails {
        @JsonProperty("default")
        private Thumbnail defaultThumb;   // "default" est un mot réservé → renommer le champ
        private Thumbnail medium;
        private Thumbnail high;
        private Thumbnail standard;
        private Thumbnail maxres;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Thumbnail {
        private String url;
        private Integer width;
        private Integer height;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageInfo {
        private int totalResults;
        private int resultsPerPage;
    }
}
