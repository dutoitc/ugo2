package ch.mno.ugo2.facebook;

import ch.mno.ugo2.dto.MetricsUpsertItem;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

public final class FacebookMetricsMapper {

    private FacebookMetricsMapper() {}

    public static MetricsUpsertItem fromVideoAndInsights(JsonNode video, Map<String, Long> insights) {
        String id = text(video, "id");

        String productType = text(video, "product_type"); // "reels" si reel
        boolean isReelFlag = video.path("is_reel").asBoolean(false);
        String platformFormat = (isReelFlag || "reels".equalsIgnoreCase(productType)) ? "REEL" : "VIDEO";

        Integer lengthSec = intOrNull(video, "length");

        Long viewsNative = pickFirst(insights,
                "total_video_views", "post_video_views", "play_count", "video_play_count", "total_plays");

        Long avgWatchSec = pickFirst(insights,
                "post_video_avg_time_watched", "avg_watch_time");

        Long totalWatchSec = pickFirst(insights,
                "post_video_view_time", "total_watch_time");

        Long reach = pickFirst(insights, "post_impressions_unique", "reach", "unique_reach");
        Long uniqueViewers = pickFirst(insights, "total_video_views_unique", "unique_video_viewers");

        Long likes = pickFirst(insights, "like_count", "post_reactions_like_total");
        Long love  = pickFirst(insights, "post_reactions_love_total");
        Long wow   = pickFirst(insights, "post_reactions_wow_total");
        Long haha  = pickFirst(insights, "post_reactions_haha_total");
        Long sad   = pickFirst(insights, "post_reactions_sad_total");
        Long angry = pickFirst(insights, "post_reactions_anger_total");

        Long reactionsTotal = sumNullable(likes, love, wow, haha, sad, angry);

        Long comments = pickFirst(insights, "comment_count", "post_comments");
        Long shares   = pickFirst(insights, "share_count", "post_shares");

        return MetricsUpsertItem.builder()
                .platform("FACEBOOK")
                .platform_format(platformFormat)
                .platform_video_id(id)
                .snapshot_at(Instant.now())
                .views_native(viewsNative)
                .avg_watch_seconds(toInt(avgWatchSec))
                .total_watch_seconds(totalWatchSec)
                .video_length_seconds(lengthSec)
                .reach(reach)
                .unique_viewers(uniqueViewers)
                .likes(likes)
                .comments(comments)
                .shares(shares)
                .reactions_total(reactionsTotal)
                .reactions_like(likes)
                .reactions_love(love)
                .reactions_wow(wow)
                .reactions_haha(haha)
                .reactions_sad(sad)
                .reactions_angry(angry)
                .build();
    }

    private static String text(JsonNode n, String f) { return n.path(f).isMissingNode() ? null : n.path(f).asText(null); }
    private static Integer intOrNull(JsonNode n, String f) { return n.path(f).canConvertToInt() ? n.path(f).asInt() : null; }
    private static Integer toInt(Long v) { return v == null ? null : (v > Integer.MAX_VALUE ? Integer.MAX_VALUE : v.intValue()); }

    @SafeVarargs private static Long pickFirst(Map<String, Long> map, String... keys) {
        if (map == null) return null;
        for (String k : keys) {
            var v = map.get(k);
            if (v != null) return v;
        }
        return null;
    }
    private static Long sumNullable(Long... a) {
        long s = 0; boolean any=false;
        for (Long v: a) { if (v!=null){ s+=v; any=true; } }
        return any ? s : null;
    }
}
