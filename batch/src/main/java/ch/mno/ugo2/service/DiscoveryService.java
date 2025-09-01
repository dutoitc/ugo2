package ch.mno.ugo2.service;

import ch.mno.ugo2.api.FacebookClient;
import ch.mno.ugo2.api.YouTubeClient;
import ch.mno.ugo2.api.InstagramClient;
import ch.mno.ugo2.api.WordPressClient;
import ch.mno.ugo2.config.AppProps;
import ch.mno.ugo2.dao.CheckpointDao;
import ch.mno.ugo2.model.SourceVideo;
import ch.mno.ugo2.repo.SourceVideoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class DiscoveryService {

  private final AppProps cfg;
  private final SourceVideoRepository sourceRepo;
  private final CheckpointDao cpDao;
  private final FacebookClient fb;
  private final YouTubeClient yt;
  private final InstagramClient ig;
  private final WordPressClient wp;
  private final ObjectMapper om = new ObjectMapper();

  public DiscoveryService(AppProps cfg, SourceVideoRepository sourceRepo, CheckpointDao cpDao,
                          FacebookClient fb, YouTubeClient yt, InstagramClient ig, WordPressClient wp) {
    this.cfg = cfg;
    this.sourceRepo = sourceRepo;
    this.cpDao = cpDao;
    this.fb = fb;
    this.yt = yt;
    this.ig = ig;
    this.wp = wp;
  }

  @Transactional
  public int discover(boolean initialMode) {
    int created = 0;
    if (cfg.getPlatforms().fb.enabled && !cfg.getPlatforms().fb.pageId.isBlank()) {
      created += discoverFacebook(initialMode);
    }
    if (cfg.getPlatforms().yt.enabled && !cfg.getPlatforms().yt.channelId.isBlank()) {
      created += discoverYouTube(initialMode);
    }
    if (cfg.getPlatforms().ig.enabled && !cfg.getPlatforms().ig.userId.isBlank()) {
      created += discoverInstagram(initialMode);
    }
    if (cfg.getPlatforms().wp.enabled && !cfg.getPlatforms().wp.baseUrl.isBlank()) {
      created += discoverWordPress(initialMode);
    }
    return created;
  }

  private int discoverFacebook(boolean initial) {
    var p = cfg.getPlatforms().fb;
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    LocalDateTime since = initial ? now.minusYears(5) : now.minusDays(cfg.getBatch().rollingDays);
    String sinceIso = since.toString();
    String untilIso = now.plusHours(1).toString();

    var pages = fb.listPublishedPostsAll(p.pageId, p.accessToken, sinceIso, untilIso, cfg.getBudgets().fbCalls / 5);
    int created = 0;
    for (var item : pages) {
      try {
        var json = om.valueToTree(item);
        var data = json.path("attachments").path("data");
        if (data.isArray()) {
          for (JsonNode att : data) {
            String mediaType = att.path("media_type").asText("");
            if (!mediaType.toLowerCase().contains("video")) continue;
            String vid = att.path("target").path("id").asText("");
            if (vid.isEmpty()) continue;
            String postId = json.path("id").asText("");
            String permalink = json.path("permalink_url").asText("");
            String createdTime = json.path("created_time").asText("");
            LocalDateTime publishedAt = createdTime.isEmpty() ? since : LocalDateTime.parse(createdTime.replace("Z",""));

            var existing = sourceRepo.findByPlatformAndPlatformSourceId("FACEBOOK", vid);
            if (existing.isEmpty()) {
              SourceVideo s = new SourceVideo();
              s.setPlatform("FACEBOOK");
              s.setPlatformSourceId(vid);
              s.setPermalinkUrl(permalink.isEmpty()? "https://facebook.com/" + postId : permalink);
              s.setTitle(null);
              s.setDescription(null);
              s.setMediaType("VIDEO");
              s.setTeaser(false);
              s.setPublishedAt(publishedAt);
              s.setDurationSeconds(null);
              s.setEtag(null);
              sourceRepo.save(s);
              created++;
            }
          }
        }
      } catch (Exception ignore) {}
    }
    cpDao.upsert("FACEBOOK","discovery", null, LocalDate.now().atStartOfDay(), null);
    return created;
  }

  private int discoverYouTube(boolean initial) {
    var p = cfg.getPlatforms().yt;
    String sinceIso = initial ? null : LocalDate.now().minusDays(cfg.getBatch().rollingDays).atStartOfDay().toString();
    var items = yt.searchAll(p.channelId, p.apiKey, sinceIso, cfg.getBudgets().ytCalls / 5);
    int created = 0;
    for (var it : items) {
      var json = om.valueToTree(it);
      String videoId = json.path("id").path("videoId").asText("");
      if (videoId.isEmpty()) continue;
      String title = json.path("snippet").path("title").asText("");
      String description = json.path("snippet").path("description").asText("");
      String publishedAt = json.path("snippet").path("publishedAt").asText("");
      LocalDateTime pub = publishedAt.isEmpty()? LocalDateTime.now(ZoneOffset.UTC) : LocalDateTime.parse(publishedAt.replace("Z",""));

      var existing = sourceRepo.findByPlatformAndPlatformSourceId("YOUTUBE", videoId);
      if (existing.isEmpty()) {
        SourceVideo s = new SourceVideo();
        s.setPlatform("YOUTUBE");
        s.setPlatformSourceId(videoId);
        s.setPermalinkUrl("https://www.youtube.com/watch?v="+videoId);
        s.setTitle(title);
        s.setDescription(description);
        s.setMediaType("VIDEO");
        s.setTeaser(false);
        s.setPublishedAt(pub);
        s.setDurationSeconds(null);
        sourceRepo.save(s);
        created++;
      }
    }
    cpDao.upsert("YOUTUBE","discovery", null, LocalDate.now().atStartOfDay(), null);
    return created;
  }

  private int discoverInstagram(boolean initial) {
    var p = cfg.getPlatforms().ig;
    var items = ig.listMediaAll(p.userId, p.accessToken, cfg.getBudgets().igCalls / 5);
    int created = 0;
    for (var it : items) {
      var json = om.valueToTree(it);
      String mediaType = json.path("media_type").asText("");
      String product = json.path("media_product_type").asText("");
      if (!"VIDEO".equalsIgnoreCase(mediaType) && !"REELS".equalsIgnoreCase(product)) continue;
      String id = json.path("id").asText("");
      String permalink = json.path("permalink").asText("");
      String caption = json.path("caption").asText("");
      String ts = json.path("timestamp").asText("");
      LocalDateTime pub = ts.isEmpty()? LocalDateTime.now(ZoneOffset.UTC) : LocalDateTime.parse(ts.replace("Z",""));
      var existing = sourceRepo.findByPlatformAndPlatformSourceId("INSTAGRAM", id);
      if (existing.isEmpty()) {
        SourceVideo s = new SourceVideo();
        s.setPlatform("INSTAGRAM");
        s.setPlatformSourceId(id);
        s.setPermalinkUrl(permalink);
        s.setTitle(caption);
        s.setDescription(caption);
        s.setMediaType("REEL");
        s.setTeaser(false);
        s.setPublishedAt(pub);
        sourceRepo.save(s);
        created++;
      }
    }
    cpDao.upsert("INSTAGRAM","discovery", null, LocalDate.now().atStartOfDay(), null);
    return created;
  }

  private int discoverWordPress(boolean initial) {
    var p = cfg.getPlatforms().wp;
    var pages = wp.listPostsAll(p.baseUrl, cfg.getBudgets().wpCalls);
    int created = 0;
    for (String body : pages) {
      try {
        JsonNode arr = om.readTree(body);
        if (arr.isArray()) {
          for (JsonNode n : arr) {
            String id = n.path("id").asText("");
            if (id.isEmpty()) continue;
            String link = n.path("link").asText("");
            String title = n.path("title").path("rendered").asText("");
            String excerpt = n.path("excerpt").path("rendered").asText("");
            String date = n.path("date").asText("");
            LocalDateTime pub = date.isEmpty()? LocalDateTime.now(ZoneOffset.UTC) : LocalDateTime.parse(date.replace("Z",""));
            var existing = sourceRepo.findByPlatformAndPlatformSourceId("WORDPRESS", id);
            if (existing.isEmpty()) {
              SourceVideo s = new SourceVideo();
              s.setPlatform("WORDPRESS");
              s.setPlatformSourceId(id);
              s.setPermalinkUrl(link);
              s.setTitle(title);
              s.setDescription(excerpt);
              s.setMediaType("VIDEO");
              s.setTeaser(false);
              s.setPublishedAt(pub);
              sourceRepo.save(s);
              created++;
            }
          }
        }
      } catch (Exception ignore) {}
    }
    cpDao.upsert("WORDPRESS","discovery", null, LocalDate.now().atStartOfDay(), null);
    return created;
  }
}
