package domain.no.iwilldoitlater.scheduling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class SchedulingConfiguration {

    //is this safe enough?
    @Value("${IWILLDOITLATER_BOT_TOKEN}")
    private String botToken;

    //putting tg bot here id ehh
    @Bean
    public TelegramClient tgClient() {
        return new OkHttpTelegramClient(botToken);
    }
}
