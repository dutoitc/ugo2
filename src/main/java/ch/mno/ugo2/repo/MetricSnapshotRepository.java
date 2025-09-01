package ch.mno.ugo2.repo;

import ch.mno.ugo2.model.MetricSnapshot;
import org.springframework.data.repository.CrudRepository;

public interface MetricSnapshotRepository extends CrudRepository<MetricSnapshot, Long> {
}
