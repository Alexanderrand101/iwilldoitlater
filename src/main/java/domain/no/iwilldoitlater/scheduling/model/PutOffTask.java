package domain.no.iwilldoitlater.scheduling.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.ZonedDateTime;

@Entity
@Data
public class PutOffTask {

    @Id
    @GeneratedValue //default is auto. Is there a reason it should not be?
    private Long id;
    private String description;
    //columns added later are null so this would need handling
    private Boolean completed;
    //Ok so the plan is. Set first fire date + period and backoff
    private ZonedDateTime nextExecutionTime;
    private int allowedDelays;
    private Integer doneDelays = 0;
    private int delayCount;
    private String delayUnit;
    private Long chatId;
}
