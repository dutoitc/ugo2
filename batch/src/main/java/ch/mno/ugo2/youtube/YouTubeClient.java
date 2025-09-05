package ch.mno.ugo2.youtube;

import ch.mno.ugo2.common.AbstractClient;
import ch.mno.ugo2.youtube.responses.ChannelsContentDetailsResponse;
import ch.mno.ugo2.youtube.responses.PlaylistItemsResponse;
import ch.mno.ugo2.youtube.responses.VideoListResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class YouTubeClient extends AbstractClient {

    private static final String BASE = "https://www.googleapis.com/youtube/v3";


    public YouTubeClient(WebClient youtubeWebClient) {
        super(youtubeWebClient);
    }

    /**
     * channels.list — part=contentDetails
     */
    public Mono<ChannelsContentDetailsResponse> channelsContentDetails(String apiKey, String channelId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE + "/channels")
                .queryParam("part", "contentDetails")
                .queryParam("id", channelId)
                .queryParam("key", apiKey)
                .build(true)
                .toUri();
        // pas d'ETag ici, c’est léger
        return get(uri, null, ChannelsContentDetailsResponse.class);
    }

    /**
     * playlistItems.list — part=contentDetails,snippet ; gère ETag via getJson()
     */
    public Mono<PlaylistItemsResponse> playlistItems(String apiKey, String playlistId, Integer maxResults, String pageToken, String etag) {
        int page = (maxResults == null ? 50 : maxResults);
        page = Math.clamp(page, 1, 50);

        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(BASE + "/playlistItems")
                .queryParam("part", "contentDetails,snippet")
                .queryParam("playlistId", playlistId)
                .queryParam("maxResults", page)
                .queryParam("key", apiKey);
        if (pageToken != null) b.queryParam("pageToken", pageToken);

        URI uri = b.build(true).toUri();
        return get(uri, etag, PlaylistItemsResponse.class);
    }

    /**
     * videos.list — part=snippet,statistics,contentDetails (IDs en CSV)
     */
    public Mono<VideoListResponse> videosList(String apiKey, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            var r = new VideoListResponse();
            r.setItems(new ArrayList<>());
            return Mono.just(r);
        }
        String joined = String.join(",", ids);
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE + "/videos")
                .queryParam("part", "snippet,statistics,contentDetails")
                .queryParam("id", joined)
                .queryParam("key", apiKey)
                .build(true)
                .toUri();
        // pas d’ETag ici pour fiabiliser la collecte des stats
        return get(uri, null, VideoListResponse.class);
    }

}
