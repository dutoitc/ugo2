package ch.mno.ugo2.youtube.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoListResponse {
    private String kind;
    private String etag;
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
        private Statistics statistics;
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
        private String categoryId;
        private String liveBroadcastContent;
        private String defaultLanguage;
        private Localized localized;
        private String defaultAudioLanguage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Localized {
        private String title;
        private String description;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Thumbnails {
        @JsonProperty("default")
        private Thumbnail defaultThumb;   // "default" → renommé
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
    public static class ContentDetails {
        private Duration duration;       // ex: PT6M11S
        private String dimension;        // "2d"
        private String definition;       // "hd"
        private String caption;          // parfois "true"/"false" (string)
        private boolean licensedContent;
        private Map<String, Object> contentRating; // objet vide ou clés variées
        private String projection;       // "rectangular"
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Statistics {
        private Long viewCount;
        private Long likeCount;
        private Long favoriteCount;
        private Long commentCount;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageInfo {
        private int totalResults;
        private int resultsPerPage;
    }
}
