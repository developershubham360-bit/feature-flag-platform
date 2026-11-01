package com.shubham.featureflagplatform.outbox;

import com.shubham.featureflagplatform.config.PropagationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PropagationProperties props;

    public OutboxPublisher(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate, PropagationProperties props) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.propagation.outbox-poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository.lockOldest(props.outboxBatchSize());
        if (batch.isEmpty()) {
            return;
        }
        List<Long> sent = new ArrayList<>();
        for (OutboxEvent row : batch) {
            if (!send(row)) {
                break;
            }
            sent.add(row.getId());
        }
    }

    private boolean send(OutboxEvent row) {
        try {
            kafkaTemplate.send(props.topic(), row.getMessageKey(), row.getPayload()).get(props.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch(Exception ex) {
            log.warn("Kafka publish failed for outbox row {} ({}), will retry next poll", row.getId(), ex.toString());
            return false;
        }
    }
    
}
