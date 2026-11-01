package com.shubham.featureflagplatform.evaluation;

import com.shubham.featureflagplatform.cache.CachedFlag;
import com.shubham.featureflagplatform.cache.CachedRule;
import com.shubham.featureflagplatform.cache.FlagCache;
import com.shubham.featureflagplatform.cache.LocalFlagCache;
import com.shubham.featureflagplatform.evaluation.dto.EvaluationRequest;
import com.shubham.featureflagplatform.evaluation.dto.EvaluationResponse;
import com.shubham.featureflagplatform.flag.Flag;
import com.shubham.featureflagplatform.flag.FlagRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EvaluationService {

    private final FlagRepository flagRepository;
    private final FlagCache flagCache;
    private final LocalFlagCache localFlagCache;

    private final Counter l1Hits;
    private final Counter l1Misses;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Timer evaluateTimer;

    public EvaluationService(FlagRepository flagRepository, FlagCache flagCache,
                             LocalFlagCache localFlagCache, MeterRegistry meterRegistry) {
        this.flagRepository = flagRepository;
        this.flagCache = flagCache;
        this.localFlagCache = localFlagCache;
        this.l1Hits = meterRegistry.counter("flag.l1", "result", "hit");
        this.l1Misses = meterRegistry.counter("flag.l1", "result", "miss");
        this.cacheHits = meterRegistry.counter("flag.cache", "result", "hit");      // Redis (L2)
        this.cacheMisses = meterRegistry.counter("flag.cache", "result", "miss");
        this.evaluateTimer = Timer.builder("flag.evaluate")
                .publishPercentiles(0.5, 0.99)          
                .register(meterRegistry);
    }

    public EvaluationResponse evaluate(EvaluationRequest req) {
        return evaluateTimer.record(() -> doEvaluate(req));
    }

    private EvaluationResponse doEvaluate(EvaluationRequest req) {
        CachedFlag flag = loadFlag(req.environment(), req.flagKey());

        if (!flag.present()) {
            return new EvaluationResponse(req.flagKey(), false, "FLAG_NOT_FOUND");
        }
        // Kill switch
        if (!flag.enabled()) {
            return new EvaluationResponse(req.flagKey(), false, "FLAG_DISABLED");
        }
        // Targeting rules — user must satisfy ALL rules (AND semantics)
        Map<String, String> attrs = req.attributes() == null ? Map.of() : req.attributes();
        for (CachedRule rule : flag.targetingRules()) {
            String userValue = attrs.get(rule.attribute());
            if (!rule.matches(userValue)) {
                return new EvaluationResponse(req.flagKey(), false, "TARGETING_NOT_MATCHED");
            }
        }
        // Percentage rollout via consistent hashing — sticky per (user, flag)
        int bucket = bucketFor(req.userId(), req.flagKey());
        if (bucket < flag.rolloutPercentage()) {
            return new EvaluationResponse(req.flagKey(), true, "ROLLOUT_MATCHED");
        }
        return new EvaluationResponse(req.flagKey(), false, "ROLLOUT_NOT_MATCHED");
    }

    private CachedFlag loadFlag(String environment, String flagKey) {
        Optional<CachedFlag> local = localFlagCache.get(environment, flagKey);
        if (local.isPresent()) {
            l1Hits.increment();
            return local.get();
        }
        l1Misses.increment();

        CachedFlag loaded = loadFromRedisOrDb(environment, flagKey);
        if (!loaded.present()) {
            return loaded;
        }
        return localFlagCache.merge(environment, flagKey, loaded);
    }

    private CachedFlag loadFromRedisOrDb(String environment, String flagKey) {
        Optional<CachedFlag> cached = flagCache.get(environment, flagKey);
        if (cached.isPresent()) {
            cacheHits.increment();
            return cached.get();
        }
        cacheMisses.increment();

        Optional<Flag> maybeFlag = flagRepository.findByKeyAndEnvironment(flagKey, environment);
        if (maybeFlag.isEmpty()) {
            flagCache.putAbsent(environment, flagKey);   // negative cache the miss
            return CachedFlag.ABSENT;
        }
        CachedFlag value = toCached(maybeFlag.get());
        flagCache.put(environment, flagKey, value);
        return value;
    }

    private CachedFlag toCached(Flag f) {
        List<CachedRule> rules = f.getTargetingRules().stream()
                .map(r -> new CachedRule(r.getAttribute(), r.getOperator(), r.getValue()))
                .toList();
        return new CachedFlag(f.getId(), true, f.isEnabled(), f.getRolloutPercentage(), rules,
                f.getVersion());
    }

    private int bucketFor(String userId, String flagKey) {
        String seed = userId + ":" + flagKey;
        return Math.floorMod(seed.hashCode(), 100);
    }
}