package ch.mno.ugo2.repo;

import ch.mno.ugo2.model.Tag;
import org.springframework.data.repository.CrudRepository;
import java.util.Optional;

public interface TagRepository extends CrudRepository<Tag, Long> {
  Optional<Tag> findByName(String name);
}
