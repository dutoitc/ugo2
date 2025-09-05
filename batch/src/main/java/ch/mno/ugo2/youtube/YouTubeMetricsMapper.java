package ch.mno.ugo2.youtube;

import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.youtube.responses.VideoListResponse;

import java.time.Instant;

public final class YouTubeMetricsMapper {

    private YouTubeMetricsMapper() {
    }

    public static MetricsUpsertItem fromVideoResource(VideoListResponse.Item item) {
        long lengthSec = item.getContentDetails().getDuration().getSeconds();
        String platformFormat = lengthSec <= 61 ? "SHORT" : "VIDEO";

        var stats = item.getStatistics();
        Long views = stats.getViewCount();
        Long likes = stats.getLikeCount();
        Long comments = stats.getCommentCount();

        return MetricsUpsertItem.builder()
                .platform("YOUTUBE")
                .platform_format(platformFormat)
                .platform_video_id(item.getId())
                .snapshot_at(Instant.now())
                .views_native(views)
                .likes(likes)
                .comments(comments)
                .video_length_seconds((int) lengthSec)
                .build();
    }

}