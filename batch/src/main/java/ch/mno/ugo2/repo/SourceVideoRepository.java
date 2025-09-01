package ch.mno.ugo2.repo;

import ch.mno.ugo2.model.SourceVideo;
import org.springframework.data.repository.CrudRepository;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface SourceVideoRepository extends CrudRepository<SourceVideo, Long> {
  Optional<SourceVideo> findByPlatformAndPlatformSourceId(String platform, String platformSourceId);

  @Query("SELECT * FROM source_video WHERE published_at BETWEEN :from AND :to")
  Iterable<SourceVideo> findByPublishedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

  @Query("SELECT * FROM source_video WHERE video_id IS NULL AND published_at BETWEEN :from AND :to")
  Iterable<SourceVideo> findUnlinkedByPublishedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
