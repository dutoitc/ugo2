package ch.mno.ugo2.dao;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Repository
public class CheckpointDao {

  private final NamedParameterJdbcTemplate jdbc;

  public CheckpointDao(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

  public record Cp(String platform, String scope, String cursor, LocalDateTime since, String extra) {}

  public Optional<Cp> get(String platform, String scope) {
    String sql = "SELECT platform,scope,cursor,since,extra FROM ingest_checkpoint WHERE platform=:p AND scope=:s";
    return jdbc.query(sql, Map.of("p", platform, "s", scope), rs -> {
      if (!rs.next()) return Optional.empty();
      return Optional.of(new Cp(
          rs.getString(1), rs.getString(2), rs.getString(3),
          rs.getTimestamp(4) != null ? rs.getTimestamp(4).toLocalDateTime() : null,
          rs.getString(5)
      ));
    });
  }

  public void upsert(String platform, String scope, String cursor, LocalDateTime since, String extra) {
    String sql = "INSERT INTO ingest_checkpoint(platform,scope,cursor,since,extra) VALUES(:p,:s,:c,:since,:e) " +
                 "ON DUPLICATE KEY UPDATE cursor=:c, since=:since, extra=:e, last_run_at=CURRENT_TIMESTAMP";
    jdbc.update(sql, Map.of("p", platform, "s", scope, "c", cursor, "since", since, "e", extra));
  }
}
