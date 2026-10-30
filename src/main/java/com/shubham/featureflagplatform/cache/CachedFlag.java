package com.shubham.featureflagplatform.cache;

import java.util.List;

public record CachedFlag(boolean present, boolean enabled, int rolloutPercentage, List<CachedRule> targetingRules, long version) {
    public static final CachedFlag ABSENT = new CachedFlag(false, false, 0, List.of(), 0);
}
