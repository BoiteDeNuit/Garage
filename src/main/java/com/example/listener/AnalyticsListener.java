package com.example.listener;

import com.example.config.KafkaTopicsConfig;
import com.example.event.CarCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsListener {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsListener.class);
    @KafkaListener(topics = KafkaTopicsConfig.CAR_CREATED,groupId = "analytics")
    public void onCarCreated(CarCreatedEvent event)
    {
        log.info("Аналитика: +1 машина марки {} в {}",event.brand(),event.createdAt());
    }
}

