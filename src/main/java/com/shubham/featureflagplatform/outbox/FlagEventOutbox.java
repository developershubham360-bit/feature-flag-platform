package com.shubham.featureflagplatform.outbox;

import com.shubham.featureflagplatform.event.FlagChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class FlagEventOutbox {
    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public FlagEventOutbox(OutboxRepository outboxRepository, JsonMapper jsonMapper) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(FlagChangedEvent event) {
        String json = jsonMapper.writeValueAsString(event);
        outboxRepository.save(new OutboxEvent(event.messageKey(), json));
    }
}
