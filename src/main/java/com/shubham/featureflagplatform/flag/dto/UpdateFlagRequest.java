package com.shubham.featureflagplatform.flag.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateFlagRequest (
    String name,
    String description,
    boolean enabled,
    @Min(0) @Max(100) int rolloutPercentage,
    @Valid List<TargetingRuleDto> targetingRules
) {}
