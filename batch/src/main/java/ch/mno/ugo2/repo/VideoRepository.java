package ch.mno.ugo2.repo;

import ch.mno.ugo2.model.Video;
import org.springframework.data.repository.CrudRepository;

public interface VideoRepository extends CrudRepository<Video, Long> {
}
