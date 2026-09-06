package com.shubham.featureflagplatform.flag.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateFlagRequest (
    @NotBlank String key,
    @NotBlank String environment,
    String name,
    String description,
    boolean enabled,
    @Min(0) @Max(100) int rolloutPercentage,
    @Valid List<TargetingRuleDto> targetingRules
) {}
