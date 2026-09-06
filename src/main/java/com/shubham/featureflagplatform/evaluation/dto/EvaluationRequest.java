package com.shubham.featureflagplatform.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record EvaluationRequest( 
    @NotBlank String flagKey,
    @NotBlank String environment,
    @NotBlank String userId, 
    Map<String, String> attributes
) {}
