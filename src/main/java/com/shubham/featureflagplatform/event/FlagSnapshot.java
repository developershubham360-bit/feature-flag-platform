package com.shubham.featureflagplatform.event;

import java.util.List;

public record FlagSnapshot(boolean enabled, int rolloutPercentage, List<RuleSnapshot> targetingRules) {
    public FlagSnapshot {
        targetingRules = targetingRules == null ? List.of() : List.copyOf(targetingRules);
    }
}