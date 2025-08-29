package domain.no.iwilldoitlater.scheduling;

import domain.no.iwilldoitlater.scheduling.model.PutOffTask;
import org.springframework.data.repository.CrudRepository;

public interface PutOffTaskRepository extends CrudRepository<PutOffTask, Long> {

}
