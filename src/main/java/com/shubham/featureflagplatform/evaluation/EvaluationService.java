package com.shubham.featureflagplatform.evaluation;

import com.shubham.featureflagplatform.evaluation.dto.EvaluationRequest;
import com.shubham.featureflagplatform.evaluation.dto.EvaluationResponse;
import com.shubham.featureflagplatform.flag.Flag;
import com.shubham.featureflagplatform.flag.FlagRepository;
import com.shubham.featureflagplatform.targeting.TargetingRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class EvaluationService {
    private final FlagRepository flagRepository;

    public EvaluationService(FlagRepository flagRepository) {
        this.flagRepository = flagRepository;
    }

    @Transactional(readOnly = true)
    public EvaluationResponse evaluate(EvaluationRequest req) {
        Optional<Flag> maybeFlag = flagRepository.findByKeyAndEnvironment(req.flagKey(), req.environment());

        // Fail-safe: unknow flag -> default OFF (so app never breaks)
        if (maybeFlag.isEmpty()) {
            return new EvaluationResponse(req.flagKey(), false, "FLAG_NOT_FOUND");
        }
        Flag flag = maybeFlag.get();

        // Kill switch: if the flag is disabled, return OFF
        if (!flag.isEnabled()) {
            return new EvaluationResponse(req.flagKey(), false, "FLAG_DISABLED");
        }

        // Evaluate targeting rules
        Map<String, String> attrs = req.attributes() == null ? Map.of() : req.attributes();
        for (TargetingRule rule: flag.getTargetingRules()) {
            String userValue = attrs.get(rule.getAttribute());
            if (!rule.matches(userValue)) {
                return new EvaluationResponse(req.flagKey(), false, "TARGETING_NOT_MATCHED");
            }
        }

        // Rollout percentage check
        int bucket = bucketFor(req.userId(), req.flagKey());
        if (bucket < flag.getRolloutPercentage()) {
            return new EvaluationResponse(req.flagKey(), true, "ROLLOUT_MATCHED");
        }

        return new EvaluationResponse(req.flagKey(), false, "ROLLOUT_NOT_MATCHED");
    }
    
    private int bucketFor(String userId, String flagKey) {
        String seed = userId + ":" + flagKey;
        return Math.floorMod(seed.hashCode(), 100);
    }
}
