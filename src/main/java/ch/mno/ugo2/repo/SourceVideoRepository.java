package ch.mno.ugo2.repo;

import ch.mno.ugo2.model.SourceVideo;
import org.springframework.data.repository.CrudRepository;
import java.util.Optional;

public interface SourceVideoRepository extends CrudRepository<SourceVideo, Long> {
  Optional<SourceVideo> findByPlatformAndPlatformSourceId(String platform, String platformSourceId);
}
