package com.shubham.featureflagplatform.cache;

import com.shubham.featureflagplatform.event.ChangeType;
import com.shubham.featureflagplatform.event.FlagChangedEvent;
import com.shubham.featureflagplatform.event.FlagSnapshot;

import java.util.List;

public record CachedFlag(long flagId, boolean present, boolean enabled, int rolloutPercentage, List<CachedRule> targetingRules, long version) {
    public static final CachedFlag ABSENT = new CachedFlag(0, false, false, 0, List.of(), 0);
    public static CachedFlag tombstone(long flagId, long version) {
        return new CachedFlag(flagId, false, false, 0, List.of(), version);
    }
    public static CachedFlag from(FlagChangedEvent event) {
        if (event.changeType() == ChangeType.DELETED) {
            return tombstone(event.flagId(), event.version());
        }
        FlagSnapshot s = event.snapshot();
        List<CachedRule> rules = s.targetingRules().stream().map(r-> new CachedRule(r.attribute(), r.operator(), r.value())).toList();
        return new CachedFlag(event.flagId(), true, s.enabled(), s.rolloutPercentage(), rules, event.version());
    }
    public int compareVersion(CachedFlag other) {
        int byId = Long.compare(flagId, other.flagId);
        return byId != 0 ? byId : Long.compare(version, other.version);
    }
}
