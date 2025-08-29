package domain.no.iwilldoitlater.userlayer;

import domain.no.iwilldoitlater.scheduling.PutOffTaskRepository;
import domain.no.iwilldoitlater.scheduling.jobscheduler.PutOffJob;
import domain.no.iwilldoitlater.scheduling.model.PutOffTask;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Component
public class SupposedlySimpleBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private final TelegramClient telegramClient;
    private final Scheduler scheduler;
    private final PutOffTaskRepository putOffTaskRepository;

    //is this dupe code?not really
    @Value("${IWILLDOITLATER_BOT_TOKEN}")
    private String botToken;

    public SupposedlySimpleBot(TelegramClient telegramClient, Scheduler scheduler, PutOffTaskRepository putOffTaskRepository) {
        this.telegramClient = telegramClient;
        this.scheduler = scheduler;
        this.putOffTaskRepository = putOffTaskRepository;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message_text = update.getMessage().getText();
            //probably need to think of how to lock this for all non-mine chat ids.
            //maybe thats why we need proxy?
            long chat_id = update.getMessage().getChatId();

            //if specific message then schedule
            //otherwise echo
            if (message_text.startsWith("schedule")) {
                scheduleMessage(chat_id, message_text);
            } else if (message_text.startsWith("complete")) {
                completeTask(chat_id, message_text);
            } else if (message_text.startsWith("list")) {
                listFailed(chat_id);
            }
            else {
                SendMessage sendMessage = SendMessage.builder().chatId(chat_id).text("echo " + message_text).build();
                try {
                    telegramClient.execute(sendMessage);
                } catch (TelegramApiException e) {
                    //obv bad
                    e.printStackTrace();
                }
            }
        }
    }

    private void completeTask(Long chat_id, String message_text) {
        try {
            String[] splitMessage = message_text.trim().split("\\|");
            Long id = Long.valueOf(splitMessage[1]);
            PutOffTask putOffTask = putOffTaskRepository.findById(id).orElseThrow();
            putOffTask.setCompleted(true);
            putOffTaskRepository.save(putOffTask);
            try {
                SendMessage sendMessage = SendMessage.builder().chatId(chat_id).text("Success").build();
                telegramClient.execute(sendMessage);
            } catch (TelegramApiException e) {
                //obv bad
                e.printStackTrace();
            }
        } catch (Exception e) {
            handleError(chat_id);
        }
    }

    private void listFailed(Long chat_id) {
        try {
            //this is ass but wahtever for now

            String result = StreamSupport.stream(putOffTaskRepository.findAll().spliterator(), false)
                    .filter(putOffTask -> !Boolean.TRUE.equals(putOffTask.getCompleted()))
                    .filter(putOffTask -> putOffTask.getDoneDelays() != null && putOffTask.getDoneDelays() > putOffTask.getAllowedDelays())
                    .map(putOffTask -> putOffTask.getDescription() + "|id:" + putOffTask.getId())
                    .collect(Collectors.joining(";\n"));
            if (StringUtils.isEmpty(result)) {
                result = "All tasks completed";
            }
            try {
                SendMessage sendMessage = SendMessage.builder().chatId(chat_id).text(result).build();
                telegramClient.execute(sendMessage);
            } catch (TelegramApiException e) {
                //obv bad
                e.printStackTrace();
            }
        } catch (Exception e) {
            handleError(chat_id);
        }
    }

    private void scheduleMessage(Long chat_id, String message_text) {
        try {
            String[] splitMessage = message_text.trim().split("\\|");
            ZonedDateTime moment = ZonedDateTime.parse(splitMessage[1]);
            int allowedDelays = Integer.parseInt(splitMessage[2]);
            int delayCountInSeconds = Integer.parseInt(splitMessage[3]);
            String description = splitMessage[4];
            PutOffTask putOffTask = new PutOffTask();
            putOffTask.setDelayCount(delayCountInSeconds);
            putOffTask.setAllowedDelays(allowedDelays);
            putOffTask.setDelayUnit("Seconds"); //Replace with enum?
            putOffTask.setDescription(description);
            putOffTask.setChatId(chat_id);
            putOffTask.setNextExecutionTime(moment);
            putOffTask = putOffTaskRepository.save(putOffTask);
            JobDetail jobDetail = JobBuilder.newJob(PutOffJob.class).usingJobData("id", putOffTask.getId()).build();
            Trigger trigger = TriggerBuilder.newTrigger().startAt(Date.from(moment.toInstant())).build();
            //this is recommended to be done within transaction else issues. Not sure.
            try {
                scheduler.scheduleJob(jobDetail, trigger);
                SendMessage sendMessage = SendMessage.builder().chatId(chat_id).text("Scheduled. at " + moment + " Id: " + putOffTask.getId()).build();
                telegramClient.execute(sendMessage);
            } catch (SchedulerException | TelegramApiException e) {
                //obv bad
                e.printStackTrace();
            }
        } catch (Exception e) {
            handleError(chat_id);
        }
    }

    //does this capture all or is error handling... SendMessage building cant really fail though?
    private void handleError(Long chat_id) {
        SendMessage sendMessage = SendMessage.builder().chatId(chat_id).text("Error").build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @AfterBotRegistration
    public void afterRegistration(BotSession botSession) {
        System.out.println("Registered bot running state is: " + botSession.isRunning());
    }
}
