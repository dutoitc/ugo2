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

  public List<Map<String,Object>> aggregatesByPresenter() {
    String sql =
      "SELECT p.display_name AS presenter, SUM(t.views_total_no_wp) AS views " +
      "FROM video_presenter vp " +
      "JOIN person p ON p.id = vp.person_id " +
      "JOIN v_video_totals_latest t ON t.video_id = vp.video_id " +
      "GROUP BY p.display_name " +
      "ORDER BY views DESC";
    return jdbc.query(sql, Map.of(), (rs, rowNum) -> Map.of("presenter", rs.getString(1), "views", rs.getLong(2)));
  }

  public List<Map<String,Object>> aggregatesByDirector() {
    String sql =
      "SELECT p.display_name AS director, SUM(t.views_total_no_wp) AS views " +
      "FROM video_director vd " +
      "JOIN person p ON p.id = vd.person_id " +
      "JOIN v_video_totals_latest t ON t.video_id = vd.video_id " +
      "GROUP BY p.display_name " +
      "ORDER BY views DESC";
    return jdbc.query(sql, Map.of(), (rs, rowNum) -> Map.of("director", rs.getString(1), "views", rs.getLong(2)));
  }
}
