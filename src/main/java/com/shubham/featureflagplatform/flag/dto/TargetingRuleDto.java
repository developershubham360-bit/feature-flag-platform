package com.shubham.featureflagplatform.flag.dto;

import com.shubham.featureflagplatform.targeting.Operator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TargetingRuleDto (
    @NotBlank String attribute,
    @NotNull Operator operator,
    @NotBlank  String value
) {}
