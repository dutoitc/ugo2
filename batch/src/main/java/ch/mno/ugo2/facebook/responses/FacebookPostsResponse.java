package ch.mno.ugo2.facebook.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class FacebookPostsResponse {
    private List<Post> data;
    private Paging paging;

    @Data
    public static class Post {
        private String id;

        @JsonProperty("created_time")
        private String createdTime;

        @JsonProperty("permalink_url")
        private String permalinkUrl;

        private Attachments attachments;
    }

    @Data
    public static class Attachments {
        private List<Attachment> data;
    }

    @Data
    public static class Attachment {
        @JsonProperty("media_type")
        private String mediaType;
        private Target target;
        private SubAttachments subattachments;

        @JsonProperty("media")
        private Media media;     // <<==== AJOUT
    }

    @Data
    public static class Media {
        private String id;       // parfois présent
        private String source;   // pour les vidéos inline
    }

    @Data
    public static class SubAttachments {
        private List<SubAttachmentsData> data;
    }

    @Data
    public static class SubAttachmentsData {
        private Target target;
    }

    @Data
    public static class Target {
        private String id;
        private String type;
    }

    @Data
    public static class Paging {
        private Cursors cursors;
        private String next;
    }

    @Data
    public static class Cursors {
        private String before;
        private String after;
    }
}
