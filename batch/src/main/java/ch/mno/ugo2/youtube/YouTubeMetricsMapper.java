package ch.mno.ugo2.youtube;

import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import ch.mno.ugo2.youtube.responses.VideoListResponse;

import java.time.Instant;

public final class YouTubeMetricsMapper {

    private YouTubeMetricsMapper() {
    }

    public static MetricsUpsertItem mapMetricsUpsertItem(VideoListResponse.Item item) {
        long lengthSec = item.getContentDetails().getDuration().getSeconds();
        String platformFormat = lengthSec <= 61 ? "SHORT" : "VIDEO";

        var stats = item.getStatistics();

        return MetricsUpsertItem.builder()
                .platform("YOUTUBE")
                .platform_format(platformFormat)
                .platform_video_id(item.getId())
                .snapshot_at(Instant.now())
                .views_native(stats.getViewCount())
                .likes(stats.getLikeCount())
                .reactions_love(stats.getFavoriteCount())
                .comments(stats.getCommentCount())
                .video_length_seconds((int) lengthSec)
                .build();
    }


    public static SourceUpsertItem mapSourceUpsertItem(VideoListResponse.Item it) {
        var s  = it.getSnippet();
        var cd = it.getContentDetails();
        return SourceUpsertItem.builder()
                .platform("YOUTUBE")
                .platform_source_id(it.getId())                    // clé de source = id vidéo
                .title(s.getTitle())
                .description(s.getDescription())
                .permalink_url("https://www.youtube.com/watch?v=" + it.getId())
                .media_type(cd.getDuration().toSeconds() <= 60 ? "SHORT" : "VIDEO")
                .duration_seconds((int) cd.getDuration().toSeconds())
                .published_at(s.getPublishedAt().toString())                  // déjà ISO-8601
                .is_teaser(0)
                .video_id(null)
                .locked(0)
                .build();
    }

}