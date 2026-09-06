package com.shubham.featureflagplatform.flag.dto;

import java.time.Instant;
import java.util.List;

public record FlagDto(
    Long id,
    String key, 
    String environment,
    String name, 
    String description,
    boolean enabled,
    int rolloutPercentage,
    List <TargetingRuleDto> targetingRules,
    long version,
    Instant createdAt,
    Instant updatedAt
) {}
