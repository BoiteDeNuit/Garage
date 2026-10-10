package com.example.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

// Что делать с сообщением, на котором слушатель упал. Без этого DefaultErrorHandler по умолчанию повторяет 10 раз
// подряд и пропускает сообщение, оставив только строку в логе.
// Теперь: две повторные попытки через секунду (сбой базы, таймаут), потом сообщение уходит в <топик>-dlt
// с заголовками об ошибке, а партиция читается дальше. Ошибки, которые повтор не исправит
// (не тот JSON, не тот тип), DefaultErrorHandler сразу отправляет в DLT, без повторов
@Configuration
public class KafkaErrorConfig implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(KafkaErrorConfig.class);
    private KafkaTemplate<Object, Object> bytesTemplate;
    // Spring Boot ставит бин CommonErrorHandler в фабрику контейнеров @KafkaListener сам
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate, MeterRegistry meterRegistry)
    {
        DefaultErrorHandler handler = new DefaultErrorHandler(deadLetters(kafkaTemplate), new FixedBackOff(1000L, 2));
        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt)
            {
                log.warn("Сообщение {}-{}@{} не обработано, попытка {}: {}", record.topic(), record.partition(), record.offset(), deliveryAttempt, ex.getMessage());
            }
            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex)
            {
                meterRegistry.counter("kafka.dead.letters", "topic", record.topic()).increment();
                log.error("Сообщение {}-{}@{} отправлено в {}-dlt: {}", record.topic(), record.partition(), record.offset(), record.topic(), ex.getMessage());
            }
        });
        return handler;
    }
    // Битый JSON не превратился в объект: в DLT уходят исходные байты, их пишет шаблон с ByteArraySerializer.
    // Остальное — объекты событий, их пишет обычный шаблон с JSON. Порядок важен: первый подходящий по типу значения
    // Отдельный бин KafkaTemplate или ProducerFactory объявлять нельзя: Spring Boot тогда не создал бы свои.
    // Поэтому шаблон — копия основного с другим сериализатором значения, а закрывается он в destroy()
    private DeadLetterPublishingRecoverer deadLetters(KafkaTemplate<Object, Object> jsonTemplate)
    {
        bytesTemplate = new KafkaTemplate<>(jsonTemplate.getProducerFactory(), Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, bytesTemplate);
        templates.put(Object.class, jsonTemplate);
        return new DeadLetterPublishingRecoverer(templates);
    }
    @Override
    public void destroy()
    {
        if(bytesTemplate != null)
        {
            bytesTemplate.destroy();
        }
    }
}
