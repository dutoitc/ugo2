package ch.mno.ugo2.repo;

import ch.mno.ugo2.model.Person;
import org.springframework.data.repository.CrudRepository;
import java.util.Optional;

public interface PersonRepository extends CrudRepository<Person, Long> {
  Optional<Person> findByDisplayName(String displayName);
}
