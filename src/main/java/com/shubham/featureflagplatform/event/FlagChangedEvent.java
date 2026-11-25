package com.shubham.featureflagplatform.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FlagChangedEvent(UUID eventId, long flagId, String flagKey, String environment, long version, ChangeType changeType, Instant occurredAt, FlagSnapshot snapshot) {
    public FlagChangedEvent {
        Objects.requireNonNull(flagKey, "flagKey");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(changeType, "changeType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if ((changeType == ChangeType.DELETED) != (snapshot == null)) {
            throw new IllegalArgumentException("Snapshot must be null for DELETED or ROLLOUT_CHANGED events");
        }
    }

    public String messageKey() {
        return environment + ":" + flagKey;
    }   
}
