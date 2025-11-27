package ch.mno.ugo2.facebook;

import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import ch.mno.ugo2.facebook.responses.VideoResponse;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Map;

public final class FacebookMetricsMapper {

    private FacebookMetricsMapper() {
    }

    /**
     * Heuristique fiable pour REEL: l’URL contient /reel/ sur Facebook.
     */
    static String guessPlatformFormat(VideoResponse v) {
        String url = v.permalinkUrl();
        if (url != null && url.toLowerCase().contains("/reel/")) return "REEL";
        // fallback: très court = REEL prob.
        Integer d = v.durationSecondsOrNull();
        if (d != null && d <= 60) return "REEL";
        return "VIDEO";
    }

    /**
     * SourceUpsertItem complet pour remplir source_video.
     */
    public static SourceUpsertItem toSource(VideoResponse v) {
        String title = StringUtils.defaultIfBlank(v.title(),
                StringUtils.abbreviate(StringUtils.defaultString(v.description()), 160));

        return SourceUpsertItem.builder()
                .platform("FACEBOOK")
                .platform_source_id(v.id())
                .title(title)
                .description(v.description())
                .permalink_url(v.permalinkUrl())
                .media_type(guessPlatformFormat(v))
                .duration_seconds(v.durationSecondsOrNull())
                .published_at(v.createdTime() != null ? v.createdTime().toString() : null) // ISO-8601
                .is_teaser(0)
                .video_id(null)
                .locked(0)
                .build();
    }

    public static MetricsUpsertItem fromVideoAndInsights(VideoResponse video, Map<String, Long> insights) {
        String pf = guessPlatformFormat(video);
        Instant now = Instant.now();

        /* ---------- VIEWS ---------- */

        Long views3s = pickFirst(insights,
                "post_video_views_3s",
                "total_video_views_3s"
        );

        Long viewsTotal = pickFirst(insights,
                "total_video_views_unique",
                "total_video_views",
                "post_video_views",
                "fb_reels_total_plays"
        );


        /* ---------- WATCH TIME ---------- */

        // total watch time (ms → seconds)
        Long watchMs = pickFirst(insights,
                "total_video_view_total_time",
                "post_video_view_time"
        );
        Long watchSeconds = watchMs != null ? watchMs / 1000 : null;

        // avg watch time (ms → seconds)
        Long avgWatchMs = pickFirst(insights,
                "total_video_avg_time_watched",
                "post_video_avg_time_watched"
        );
        Integer avgWatchSeconds = avgWatchMs != null ? (int) (avgWatchMs / 1000) : 0;

        /* ---------- INTERACTIONS ---------- */

        Long comments = pickFirst(insights,
                "total_video_stories_by_action_type_comment",
                "total_video_comments",
                "post_comments",
                "post_video_social_actions_COMMENT"
        );

        Long shares = pickFirst(insights,
                "total_video_stories_by_action_type_share",
                "post_shares",
                "post_video_social_actions_SHARE"
        );

        Long likes = pickFirst(insights,
                "total_video_reactions_by_type_total_like",
                "total_video_likes",
                "post_likes"
        );

        /* ---------- NEW: REELS LIKE/LOVE BREAKDOWN ---------- */

        Long reactsLike = pickFirst(insights,
                "total_video_reactions_by_type_total_like",
                "post_video_likes_by_reaction_type_REACTION_LIKE"
        );

        Long reactsLove = pickFirst(insights,
                "total_video_reactions_by_type_total_love",
                "post_video_likes_by_reaction_type_REACTION_LOVE"
        );

        /* ---------- GENERAL REACTIONS ---------- */

        Long reactsTotal = pickFirst(insights,
                "total_video_reactions_by_type_total",
                "post_reactions_by_type_total"
        );

        /* ---------- NEW: FACEBOOK REACH (critique pour éviter snapshot vide) ---------- */

        Long reach = pickFirst(insights, "post_impressions_unique");

        /* ---------- BUILD SNAPSHOT ---------- */

        return MetricsUpsertItem.builder()
                .platform("FACEBOOK")
                .platform_format(pf)
                .platform_video_id(video.id())
                .snapshot_at(now)

                .avg_watch_seconds(avgWatchSeconds)
                .total_watch_seconds(watchSeconds)

                .views_native(viewsTotal != null ? viewsTotal : views3s)
                .legacy_views_3s(views3s)
                .video_length_seconds(video.durationSecondsOrNull())

                .likes(likes)

                // breakdown
                .reactions_like(reactsLike)
                .reactions_love(reactsLove)
                .reactions_haha(pickFirst(insights, "total_video_reactions_by_type_total_haha"))
                .reactions_angry(pickFirst(insights, "total_video_reactions_by_type_total_angry"))
                .reactions_wow(pickFirst(insights, "total_video_reactions_by_type_total_wow"))
                .reactions_sad(pickFirst(insights, "total_video_reactions_by_type_total_sad"))

                .comments(comments)
                .shares(shares)

                // NEW: reach
                .reach(reach)

                .reactions_total(reactsTotal)
                .build();
    }


    private static Integer pickFirstInt(Map<String, Long> insights, String... keys) {
        var l = pickFirst(insights, keys);
        if (l == null) return 0;
        return l.intValue();
    }

    @SafeVarargs
    private static Long pickFirst(Map<String, Long> map, String... keys) {
        if (map == null) return null;
        for (String k : keys) {
            var v = map.get(k);
            if (v != null) return v;
        }
        return null;
    }

}
