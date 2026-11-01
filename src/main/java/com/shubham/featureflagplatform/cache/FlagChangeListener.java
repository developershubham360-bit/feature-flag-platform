package com.shubham.featureflagplatform.cache;

import com.shubham.featureflagplatform.config.PropagationProperties;
import com.shubham.featureflagplatform.event.FlagChangedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class FlagChangeListener implements ConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(FlagChangeListener.class);

    private final LocalFlagCache localFlagCache;
    private final JsonMapper jsonMapper;
    private final Duration replayWindow;

    private final Counter applied;
    private final Counter stale;
    private final Counter invalid;
    private final Timer propagation;

    public FlagChangeListener(LocalFlagCache localFlagCache, JsonMapper jsonMapper,
                              PropagationProperties props, MeterRegistry meterRegistry) {
        this.localFlagCache = localFlagCache;
        this.jsonMapper = jsonMapper;
        this.replayWindow = Duration.ofSeconds(props.replayWindowSeconds());
        this.applied = meterRegistry.counter("flag.events", "result", "applied");
        this.stale = meterRegistry.counter("flag.events", "result", "stale");
        this.invalid = meterRegistry.counter("flag.events", "result", "invalid");
        // change committed -> applied here. The README's "propagation latency" number.
        this.propagation = Timer.builder("flag.propagation")
                .publishPercentiles(0.5, 0.99)
                .register(meterRegistry);
    }

    @KafkaListener(topics = "${app.propagation.topic:flag-changes}", groupId = "flag-l1-${random.uuid}")
    public void onMessage(String payload) {
        FlagChangedEvent event;
        try {
            event = jsonMapper.readValue(payload, FlagChangedEvent.class);
        } catch (JacksonException ex) {
            invalid.increment();
            log.error("Skipping unreadable flag event: {}", payload, ex);
            return;
        }

        CachedFlag incoming = CachedFlag.from(event);
        CachedFlag winner = localFlagCache.merge(event.environment(), event.flagKey(), incoming);

        // Reference check on purpose: merge() returns OUR object only if it stored it.
        if (winner == incoming) {
            applied.increment();
            propagation.record(Duration.between(event.occurredAt(), Instant.now()));
            log.debug("Applied {} {}:{} v{} (event {})", event.changeType(), event.environment(),
                    event.flagKey(), event.version(), event.eventId());
        } else {
            stale.increment();   // duplicate or out-of-order: we already have this or newer
        }
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        long fromMillis = System.currentTimeMillis() - replayWindow.toMillis();
        callback.seekToTimestamp(assignments.keySet(), fromMillis);
    }
}
