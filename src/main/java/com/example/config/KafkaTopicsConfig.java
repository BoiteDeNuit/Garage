package com.example.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {
    public static final String CAR_CREATED = "car-created";
    @Bean
    public NewTopic carCreatedTopic() {
        return TopicBuilder.name(CAR_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

}
