package com.shubham.featureflagplatform.outbox;

import com.shubham.featureflagplatform.config.PropagationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock OutboxRepository outboxRepository;
    @Mock KafkaTemplate<String, String> kafkaTemplate;

    OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxRepository, kafkaTemplate,
                new PropagationProperties("flag-changes", 3, 500, 100, 1_000, 60));
    }

    /** The id is normally set by the database; tests set it directly. */
    private static OutboxEvent row(long id, String key) {
        OutboxEvent e = new OutboxEvent(key, "{\"n\":" + id + "}");
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private static CompletableFuture<SendResult<String, String>> ok() {
        return CompletableFuture.completedFuture(null);   // only completion matters here
    }

    @Test
    void sendsInOrder_thenDeletesSentRows() {
        when(outboxRepository.lockOldest(100)).thenReturn(List.of(row(1, "prod:a"), row(2, "prod:b")));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class))).thenReturn(ok());

        publisher.publishPending();

        InOrder order = inOrder(kafkaTemplate);
        order.verify(kafkaTemplate).send("flag-changes", "prod:a", "{\"n\":1}");
        order.verify(kafkaTemplate).send("flag-changes", "prod:b", "{\"n\":2}");
        verify(outboxRepository).deleteAllByIdInBatch(List.of(1L, 2L));
    }

    @Test
    void stopsAtFirstFailure_andDeletesOnlyWhatWasSent() {
        when(outboxRepository.lockOldest(100))
                .thenReturn(List.of(row(1, "prod:a"), row(2, "prod:a"), row(3, "prod:b")));
        when(kafkaTemplate.send("flag-changes", "prod:a", "{\"n\":1}")).thenReturn(ok());
        when(kafkaTemplate.send("flag-changes", "prod:a", "{\"n\":2}"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        publisher.publishPending();

        verify(kafkaTemplate, never()).send("flag-changes", "prod:b", "{\"n\":3}");   // not attempted
        verify(outboxRepository).deleteAllByIdInBatch(List.of(1L));                    // 2 and 3 retry
    }

    @Test
    void emptyOutbox_sendsNothing() {
        when(outboxRepository.lockOldest(100)).thenReturn(List.of());

        publisher.publishPending();

        verifyNoInteractions(kafkaTemplate);
        verify(outboxRepository, never()).deleteAllByIdInBatch(any());
    }
}