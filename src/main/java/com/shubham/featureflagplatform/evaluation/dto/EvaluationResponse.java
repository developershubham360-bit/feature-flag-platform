package com.shubham.featureflagplatform.evaluation.dto;

public record EvaluationResponse(
    String flagKey,
    boolean enabled,
    String reason
) {}
