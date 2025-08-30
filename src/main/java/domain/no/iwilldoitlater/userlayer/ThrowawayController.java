package domain.no.iwilldoitlater.userlayer;

import domain.no.iwilldoitlater.scheduling.PutOffTaskRepository;
import domain.no.iwilldoitlater.scheduling.jobscheduler.PrintJob;
import domain.no.iwilldoitlater.scheduling.jobscheduler.PutOffJob;
import domain.no.iwilldoitlater.scheduling.model.PutOffTask;
import org.quartz.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.time.ZonedDateTime;
import java.time.temporal.TemporalUnit;
import java.util.Date;

import static org.quartz.DateBuilder.futureDate;

@RestController
@RequestMapping("/tasks")
public class ThrowawayController {
    private final Scheduler scheduler;
    private final PutOffTaskRepository putOffTaskRepository;

    public ThrowawayController(Scheduler scheduler, PutOffTaskRepository putOffTaskRepository) {
        this.scheduler = scheduler;
        this.putOffTaskRepository = putOffTaskRepository;
    }

    //mpve the whole code to service
    @ResponseStatus(HttpStatus.CREATED)
    @GetMapping("/schedule")
    public Long schedule(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime dateTime) throws SchedulerException {
        //save and schedule. Probably needs to be done together in a transaction... Or maybe not?
        //If scheduling fails we can notify about it and then retry. Or even autorecover
        PutOffTask putOffTask = new PutOffTask();
        putOffTask.setDelayCount(30);
        putOffTask.setAllowedDelays(3);
        putOffTask.setDelayUnit("Seconds"); //Replace with enum?
        putOffTask.setDescription("Test");
        putOffTask.setNextExecutionTime(dateTime);
        putOffTask = putOffTaskRepository.save(putOffTask);
        JobDetail jobDetail = JobBuilder.newJob(PutOffJob.class).usingJobData("id", putOffTask.getId()).build();
        Trigger trigger = TriggerBuilder.newTrigger().startAt(Date.from(dateTime.toInstant())).build();
        //this is reccomended to be done within transaction else issues. Not sure.
        scheduler.scheduleJob(jobDetail, trigger);
        return putOffTask.getId();
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{id}/complete")
    public void schedule(@PathVariable Long id) throws SchedulerException {
        PutOffTask putOffTask = putOffTaskRepository.findById(id).orElseThrow(() -> //replace with actual exception with mapping
                new RuntimeException());
        putOffTask.setCompleted(true);
        putOffTaskRepository.save(putOffTask);
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/ping")
    public String ping() {
        return "Its alive!";
    }
}
