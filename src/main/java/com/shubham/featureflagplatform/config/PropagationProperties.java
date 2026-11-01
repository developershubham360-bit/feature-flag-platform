package com.shubham.featureflagplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.propagation")
public record PropagationProperties (
    @DefaultValue("flag-changes") String topic,
    @DefaultValue("3") int partitions,
    @DefaultValue("500") long outboxPollIntervalMs, 
    @DefaultValue("100") int outboxBatchSize,
    @DefaultValue("10000") long sendTimeoutMs,
    @DefaultValue("60") int replayWindowSeconds
) {}
