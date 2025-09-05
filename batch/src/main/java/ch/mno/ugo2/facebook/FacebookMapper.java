package ch.mno.ugo2.facebook;

import ch.mno.ugo2.dto.MetricsUpsertItem;
import ch.mno.ugo2.dto.SourceUpsertItem;
import ch.mno.ugo2.facebook.dto.FbInsights;
import ch.mno.ugo2.facebook.dto.FbVideo;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Locale;

@Component
public class FacebookMapper {

  public SourceUpsertItem toSource(FbVideo v, String mediaType) {
    return SourceUpsertItem.builder()
      .platform("FACEBOOK")
      .platform_source_id(v.id())
      .title(nvl(v.title(), "(sans titre)"))
      .description(v.description())
      .permalink_url(v.permalinkUrl())
      .media_type(mediaType.toUpperCase(Locale.ROOT))
      .duration_seconds(v.lengthSeconds() == null ? null : (int)Math.round(v.lengthSeconds()))
      .published_at(v.createdTime() == null ? null : v.createdTime().toInstant().toString())
      .is_teaser(0)
      .locked(0)
      .build();
  }

  public MetricsUpsertItem toSnapshot(FbVideo v, FbInsights ins, OffsetDateTime at) {
    Integer views = ins == null ? null : coalesce(
      ins.firstValue("total_video_views"),                   // standard (≈ «3s»)
      ins.firstValue("total_video_views_unique")             // fallback si besoin
    );
    Integer comments = null; // vous pouvez compléter si chargé ailleurs
    Integer shares   = null;
    Integer reacts   = null;

    return MetricsUpsertItem.builder()
      .platform("FACEBOOK")
      .platform_video_id(v.id())
      .snapshot_at(at.toInstant())
            .views_native(views==null?0:views.longValue())
      .comments(comments==null?0:comments.longValue())
      .shares(shares==null?0:shares.longValue())
      // .reactions(reacts) // TODO
      .build();
  }

  private static Integer coalesce(Integer... vals) {
    for (var v : vals) if (v != null) return v; return null;
  }

  private static String nvl(String s, String d) { return (s==null || s.isBlank()) ? d : s; }
}
