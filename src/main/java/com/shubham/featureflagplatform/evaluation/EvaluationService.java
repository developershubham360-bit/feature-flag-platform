package com.shubham.featureflagplatform.evaluation;

import com.shubham.featureflagplatform.cache.CachedFlag;
import com.shubham.featureflagplatform.cache.CachedRule;
import com.shubham.featureflagplatform.cache.FlagCache;
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

    // Metrics — exposed at /actuator/metrics (Stage 2's "measured improvement" story).
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Timer evaluateTimer;

    public EvaluationService(FlagRepository flagRepository, FlagCache flagCache,
                             MeterRegistry meterRegistry) {
        this.flagRepository = flagRepository;
        this.flagCache = flagCache;
        this.cacheHits = meterRegistry.counter("flag.cache", "result", "hit");
        this.cacheMisses = meterRegistry.counter("flag.cache", "result", "miss");
        this.evaluateTimer = meterRegistry.timer("flag.evaluate");
    }

    /**
     * NOTE: intentionally NOT @Transactional.
     * On a cache HIT we must not touch the database at all — and opening a
     * @Transactional boundary here would acquire a Hikari connection on every hot-path
     * call, defeating the point of the cache. On a cache MISS the single repository call
     * runs in its own (Spring Data) transaction; because Flag.targetingRules is EAGER it
     * is fully initialized by that call, so we can map it to CachedFlag safely afterward.
     * (If targetingRules ever becomes LAZY, move the DB read behind a transactional
     *  boundary in a SEPARATE bean — a @Transactional method called from within this same
     *  bean would be bypassed by Spring's proxy: the self-invocation gotcha.)
     */
    public EvaluationResponse evaluate(EvaluationRequest req) {
        return evaluateTimer.record(() -> doEvaluate(req));
    }

    private EvaluationResponse doEvaluate(EvaluationRequest req) {
        CachedFlag flag = loadFlag(req.environment(), req.flagKey());

        // Fail-safe: unknown flag -> default OFF (app never breaks)
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

    /** Cache-aside: Redis first; on miss go to Postgres and populate (real OR negative). */
    private CachedFlag loadFlag(String environment, String flagKey) {
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
        return new CachedFlag(true, f.isEnabled(), f.getRolloutPercentage(), rules, f.getVersion());
    }

    /**
     * Deterministic bucket 0..99. Same (userId, flagKey) always maps to the same bucket,
     * so a user's assignment is STICKY across evaluations and across service instances.
     * (Unchanged from Stage 1.)
     */
    private int bucketFor(String userId, String flagKey) {
        String seed = userId + ":" + flagKey;
        return Math.floorMod(seed.hashCode(), 100);
    }
}