package ch.mno.ugo2.dao;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AggregateDao {

  private final NamedParameterJdbcTemplate jdbc;

  public AggregateDao(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Map<String,Object> videoTotals(long videoId) {
    String sql = "SELECT * FROM v_video_totals_latest WHERE video_id=:id";
    return jdbc.query(sql, Map.of("id", videoId), rs -> rs.next()
        ? Map.of(
            "video_id", rs.getLong("video_id"),
            "views_total_no_wp", rs.getLong("views_total_no_wp"),
            "views_wp", rs.getLong("views_wp"),
            "comments_total", rs.getLong("comments_total"),
            "reactions_total", rs.getLong("reactions_total"),
            "shares_total", rs.getLong("shares_total"),
            "saves_total", rs.getLong("saves_total")
        )
        : Map.of());
  }

  public List<Map<String,Object>> topVideosByViews(int limit) {
    String sql = "SELECT v.video_id, v.views_total_no_wp FROM v_video_totals_latest v ORDER BY v.views_total_no_wp DESC LIMIT :lim";
    return jdbc.query(sql, Map.of("lim", limit),
        (rs, rowNum) -> Map.of("video_id", rs.getLong(1), "views_total_no_wp", rs.getLong(2)));
  }

  public List<Map<String,Object>> aggregatesByPresentateur() {
    String sql =
      "SELECT p.display_name AS presentateur, SUM(t.views_total_no_wp) AS views " +
      "FROM video_presenter vp " +
      "JOIN person p ON p.id = vp.person_id " +
      "JOIN v_video_totals_latest t ON t.video_id = vp.video_id " +
      "GROUP BY p.display_name " +
      "ORDER BY views DESC";
    return jdbc.query(sql, Map.of(), (rs, rowNum) -> Map.of("presentateur", rs.getString(1), "views", rs.getLong(2)));
  }

  public List<Map<String,Object>> aggregatesByRealisateur() {
    String sql =
      "SELECT p.display_name AS realisateur, SUM(t.views_total_no_wp) AS views " +
      "FROM video_realisateur vr " +
      "JOIN person p ON p.id = vr.person_id " +
      "JOIN v_video_totals_latest t ON t.video_id = vr.video_id " +
      "GROUP BY p.display_name " +
      "ORDER BY views DESC";
    return jdbc.query(sql, Map.of(), (rs, rowNum) -> Map.of("realisateur", rs.getString(1), "views", rs.getLong(2)));
  }
}
