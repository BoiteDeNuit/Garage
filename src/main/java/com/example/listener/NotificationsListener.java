package com.example.listener;

import com.example.config.KafkaTopicsConfig;
import com.example.event.CarCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationsListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationsListener.class);
    @KafkaListener(topics = KafkaTopicsConfig.CAR_CREATED,groupId = "notifications")
    public void onCarCreated(CarCreatedEvent event)
    {
        log.info("Уведомление: машина добавлена {} {} (id={})",event.brand(),event.model(),event.id());
    }
}
