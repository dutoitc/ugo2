package ch.mno.ugo2.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OverrideItem {
    private String source_platform;
    private String source_platform_id;
    private String action;           // LINK | UNLINK | TEASER | MAIN
    private Long target_video_id;    // optional
    private Integer lock;            // optional 0/1
}
