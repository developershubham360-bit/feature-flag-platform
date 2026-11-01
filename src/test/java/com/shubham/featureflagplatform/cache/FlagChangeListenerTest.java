package com.shubham.featureflagplatform.cache;

import com.shubham.featureflagplatform.config.CacheProperties;
import com.shubham.featureflagplatform.config.PropagationProperties;
import com.shubham.featureflagplatform.event.ChangeType;
import com.shubham.featureflagplatform.event.FlagChangedEvent;
import com.shubham.featureflagplatform.event.FlagSnapshot;
import com.shubham.featureflagplatform.event.RuleSnapshot;
import com.shubham.featureflagplatform.targeting.Operator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FlagChangeListenerTest {

    // A real Jackson 3 mapper (not a mock), so the test also proves the event JSON round-trips.
    private final JsonMapper json = JsonMapper.builder().build();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private LocalFlagCache l1;
    private FlagChangeListener listener;

    @BeforeEach
    void setUp() {
        l1 = new LocalFlagCache(new CacheProperties(300, 30, 60));
        listener = new FlagChangeListener(l1, json,
                new PropagationProperties("flag-changes", 3, 500, 100, 10_000, 60), registry);
    }

    private String updated(long version, int rollout) {
        FlagSnapshot snapshot = new FlagSnapshot(true, rollout,
                List.of(new RuleSnapshot("country", Operator.EQUALS, "IN")));
        return json.writeValueAsString(new FlagChangedEvent(UUID.randomUUID(), 1, "checkout-v2",
                "prod", version, ChangeType.UPDATED, Instant.now(), snapshot));
    }

    private double count(String result) {
        return registry.counter("flag.events", "result", result).count();
    }

    @Test
    void updateEvent_isAppliedToL1() {
        listener.onMessage(updated(2, 25));

        CachedFlag cached = l1.get("prod", "checkout-v2").orElseThrow();
        assertThat(cached.version()).isEqualTo(2L);
        assertThat(cached.rolloutPercentage()).isEqualTo(25);
        assertThat(cached.targetingRules()).hasSize(1);
        assertThat(count("applied")).isEqualTo(1.0);
    }

    @Test
    void duplicateEvent_isCountedStale() {
        String event = updated(2, 25);
        listener.onMessage(event);
        listener.onMessage(event);              // the outbox re-sent it

        assertThat(count("applied")).isEqualTo(1.0);
        assertThat(count("stale")).isEqualTo(1.0);
    }

    @Test
    void deleteEvent_storesTombstone() {
        listener.onMessage(updated(2, 25));
        listener.onMessage(json.writeValueAsString(new FlagChangedEvent(UUID.randomUUID(), 1,
                "checkout-v2", "prod", 3, ChangeType.DELETED, Instant.now(), null)));

        assertThat(l1.get("prod", "checkout-v2")).get()
                .extracting(CachedFlag::present).isEqualTo(false);
    }

    @Test
    void unreadableEvent_isSkipped_notThrown() {
        assertThatCode(() -> listener.onMessage("{not json")).doesNotThrowAnyException();
        // Valid JSON but a broken contract: an UPDATED event without a snapshot.
        assertThatCode(() -> listener.onMessage(
                "{\"flagId\":1,\"flagKey\":\"f\",\"environment\":\"prod\",\"version\":1,"
                        + "\"changeType\":\"UPDATED\",\"occurredAt\":\"2026-10-31T10:00:00Z\"}"))
                .doesNotThrowAnyException();

        assertThat(count("invalid")).isEqualTo(2.0);
    }
}