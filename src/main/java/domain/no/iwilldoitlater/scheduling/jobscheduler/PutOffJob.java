package domain.no.iwilldoitlater.scheduling.jobscheduler;

import domain.no.iwilldoitlater.scheduling.PutOffTaskRepository;
import domain.no.iwilldoitlater.scheduling.model.PutOffTask;
import org.quartz.*;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.temporal.ChronoUnit;
import java.util.Date;

public class PutOffJob implements Job {
    private final Scheduler scheduler;
    private final PutOffTaskRepository putOffTaskRepository;
    private final TelegramClient telegramClient;

    public PutOffJob(Scheduler scheduler, PutOffTaskRepository putOffTaskRepository, TelegramClient telegramClient) {
        this.scheduler = scheduler;
        this.putOffTaskRepository = putOffTaskRepository;
        this.telegramClient = telegramClient;
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        //obv move to service. And handling. And race condtion. And transaction?
        System.out.println("Firedd");
        PutOffTask putOffTask = putOffTaskRepository.findById(jobExecutionContext.getJobDetail().getJobDataMap().getLong("id"))
                .orElseThrow();
        if (Boolean.TRUE.equals(putOffTask.getCompleted())) {
            return;
        }
        System.out.println(putOffTask.getDescription());
        if (putOffTask.getChatId() != null) {
            SendMessage sendMessage = SendMessage.builder().chatId(putOffTask.getChatId()).text(putOffTask.getDescription()).build();
            try {
                telegramClient.execute(sendMessage);
            } catch (TelegramApiException e) {
                //failed and task is not rescheduled
                //throw new JobExecutionException(e);
                e.printStackTrace();
            }
        }
        putOffTask.setDoneDelays(putOffTask.getDoneDelays() + 1);
        putOffTaskRepository.save(putOffTask);
        if (putOffTask.getAllowedDelays() >= putOffTask.getDoneDelays()) {
            //ok so now I need to ensure that job fires only once. And test it. Or failing that make sure consistent triggers are produced
            //so maybe it is better to prebuild schedule
            //but then I have to work around the edge case. Whch seems to be rare..
            //lets do it likt this for now
            JobDetail jobDetail = JobBuilder.newJob(PutOffJob.class).usingJobData("id", putOffTask.getId()).build();
            Trigger trigger = TriggerBuilder.newTrigger().startAt(Date.from(putOffTask.getNextExecutionTime().toInstant().plus(putOffTask.getDelayCount() * putOffTask.getDoneDelays(), ChronoUnit.SECONDS))).build();
            //this is reccomended to be done within transaction else issues. Not sure.
            try {
                scheduler.scheduleJob(jobDetail, trigger);
            } catch (SchedulerException e) {
                throw new JobExecutionException(e);
            }
        }
    }
}
