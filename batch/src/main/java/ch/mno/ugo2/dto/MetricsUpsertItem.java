package ch.mno.ugo2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetricsUpsertItem {
    String platform;           // "YOUTUBE" | "FACEBOOK" | "INSTAGRAM" | "TIKTOK"
    String platform_format;    // "VIDEO" | "SHORT" | "REEL" (optionnel mais recommandé)
    Long   source_video_id;    // soit ça…
    String platform_video_id;  // … soit ce couple (avec platform)

    Instant snapshot_at;       // optionnel (Z); sinon serveur = now UTC

    Long    views_native;
    Integer avg_watch_seconds;
    Long    total_watch_seconds;
    Integer video_length_seconds;

    Long reach;
    Long unique_viewers;

    Long likes;
    Long comments;
    Long shares;

    Long reactions_total;
    Long reactions_like;
    Long reactions_love;
    Long reactions_wow;
    Long reactions_haha;
    Long reactions_sad;
    Long reactions_angry;

    // Compat FB VIDEO (vues 3s historiques)
    Long legacy_views_3s;
}
