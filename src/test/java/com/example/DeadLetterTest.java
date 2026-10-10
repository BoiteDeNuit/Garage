package com.example;

import com.example.event.ListingPublishedEvent;
import com.example.model.AppUser;
import com.example.model.Role;
import com.example.support.IntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.http.MediaType;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Сообщения, на которых слушатель падает, не блокируют партицию и не теряются: они в <топик>-dlt
class DeadLetterTest extends IntegrationTest {
    private static final String DLT = "listing-published-dlt";
    @Autowired
    KafkaConnectionDetails kafkaConnection;

    // Не JSON: повтор не поможет, поэтому сразу в DLT с исходными байтами. Следующее нормальное событие обрабатывается
    @Test
    void poisonMessageGoesToDeadLetterTopicAndConsumerMovesOn() throws Exception
    {
        long key = ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
        byte[] poison = "это не JSON".getBytes(StandardCharsets.UTF_8);
        try (KafkaProducer<Long, byte[]> producer = new KafkaProducer<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap()),
                new LongSerializer(), new ByteArraySerializer()))
        {
            producer.send(new ProducerRecord<>("listing-published", key, poison)).get();
        }

        ConsumerRecord<Long, byte[]> dead = awaitDeadLetter(key, "notifications");
        assertThat(dead.value()).isEqualTo(poison);
        assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_FQCN)).contains("DeserializationException");

        Long next = createAndPublish(createUser(Role.USER));
        verify(notificationsListener, timeout(15000)).onListingPublished(argThat((ListingPublishedEvent event) -> event.listingId().equals(next)));
    }

    // Слушатель падает на событии: первая попытка и две повторных через секунду, потом DLT. Четвёртой попытки нет
    @Test
    void failingListenerIsRetriedThenDeadLettered() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long id = createDraft(seller);
        doThrow(new IllegalStateException("база недоступна"))
                .when(notificationsListener).onListingPublished(argThat((ListingPublishedEvent event) -> event != null && event.listingId().equals(id)));

        mockMvc.perform(post("/api/listings/{id}/publish", id).header("Authorization", bearer(seller))).andExpect(status().isOk());

        verify(notificationsListener, timeout(20000).times(3)).onListingPublished(argThat((ListingPublishedEvent event) -> event.listingId().equals(id)));
        ConsumerRecord<Long, byte[]> dead = awaitDeadLetter(id, "notifications");
        assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).contains("база недоступна");
        assertThat(new String(dead.value(), StandardCharsets.UTF_8)).contains("\"listingId\":" + id);
        verify(notificationsListener, after(3000).times(3)).onListingPublished(argThat((ListingPublishedEvent event) -> event.listingId().equals(id)));
    }

    private ConsumerRecord<Long, byte[]> awaitDeadLetter(long key, String group)
    {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<Long, byte[]> consumer = new KafkaConsumer<>(config, new LongDeserializer(), new ByteArrayDeserializer()))
        {
            consumer.subscribe(List.of(DLT));
            ConsumerRecord<Long, byte[]>[] found = new ConsumerRecord[1];
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<Long, byte[]> record : consumer.poll(Duration.ofMillis(500)))
                {
                    if (record.key() != null && record.key() == key && group.equals(header(record, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP)))
                    {
                        found[0] = record;
                    }
                }
                return found[0] != null;
            });
            return found[0];
        }
    }

    private String header(ConsumerRecord<?, ?> record, String name)
    {
        return Optional.ofNullable(record.headers().lastHeader(name)).map(Header::value).map(value -> new String(value, StandardCharsets.UTF_8)).orElse("");
    }

    private String bootstrap()
    {
        return String.join(",", kafkaConnection.getBootstrapServers());
    }

    private Long createDraft(AppUser seller) throws Exception
    {
        String body = """
                {"brand": "%s", "model": "Supra", "horsePower": 320, "year": 1998, "mileageKm": 154000,
                 "price": 4500000, "city": "Самара"}""".formatted(uniqueBrand());
        return objectMapper.readTree(mockMvc.perform(post("/api/listings").header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createAndPublish(AppUser seller) throws Exception
    {
        Long id = createDraft(seller);
        mockMvc.perform(post("/api/listings/{id}/publish", id).header("Authorization", bearer(seller))).andExpect(status().isOk());
        return id;
    }
}
