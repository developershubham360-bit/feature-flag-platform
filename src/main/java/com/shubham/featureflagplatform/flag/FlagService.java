package com.shubham.featureflagplatform.flag;

import com.shubham.featureflagplatform.audit.AuditLog;
import com.shubham.featureflagplatform.audit.AuditLogRepository;
import com.shubham.featureflagplatform.error.FlagNotFoundException;
import com.shubham.featureflagplatform.flag.dto.CreateFlagRequest;
import com.shubham.featureflagplatform.flag.dto.FlagDto;
import com.shubham.featureflagplatform.flag.dto.UpdateFlagRequest;
import com.shubham.featureflagplatform.flag.dto.TargetingRuleDto;
import com.shubham.featureflagplatform.targeting.TargetingRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class FlagService {
    private final FlagRepository flagRepository;
    private final AuditLogRepository auditLogRepository;

    public FlagService(FlagRepository flagRepository, AuditLogRepository auditLogRepository) {
        this.flagRepository = flagRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true) 
    public List<FlagDto> findAll() {
        return flagRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional (readOnly = true)
    public FlagDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional 
    public FlagDto create(CreateFlagRequest req) {
        Flag flag = new Flag(req.key(), req.environment(), req.name(), req.description(), req.enabled(), req.rolloutPercentage());
        flag.setTargetingRules(toEntityRules(req.targetingRules()));
        Flag saved = flagRepository.save(flag);
        audit(saved.getKey(), saved.getEnvironment(), "CREATE", "admin");
        return toDto(saved);
    }

    @Transactional
    public FlagDto update(Long id, UpdateFlagRequest req) {
        Flag flag = getOrThrow(id);
        flag.setName(req.name());
        flag.setDescription(req.description());
        flag.setEnabled(req.enabled());
        flag.setRolloutPercentage(req.rolloutPercentage());
        flag.setTargetingRules(toEntityRules(req.targetingRules()));
        flag.setVersion(flag.getVersion() + 1);
        flag.setUpdatedAt(Instant.now());
        audit(flag.getKey(), flag.getEnvironment(), "UPDATE", "admin");
        return toDto(flag);
    }

    @Transactional
    public void delete(Long id) {
        Flag flag = getOrThrow(id);
        flagRepository.delete(flag);
        audit(flag.getKey(), flag.getEnvironment(), "DELETE", "admin");
    }

    private Flag getOrThrow(Long id) {
        return flagRepository.findById(id).orElseThrow(() -> new FlagNotFoundException(id));
    }

    private void audit(String key, String environment, String action, String user) {
        auditLogRepository.save(new AuditLog(key, environment, action, user));
    }

    private List<TargetingRule> toEntityRules(List<TargetingRuleDto> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream().map(dto -> new TargetingRule(dto.attribute(), dto.operator(), dto.value())).toList();
    }

    private FlagDto toDto(Flag f) {
        List<TargetingRuleDto> targetingRules = f.getTargetingRules().stream()
                .map(r -> new TargetingRuleDto(r.getAttribute(), r.getOperator(), r.getValue()))
                .toList();
        return new FlagDto(
                f.getId(),
                f.getKey(),
                f.getEnvironment(),
                f.getName(),
                f.getDescription(),
                f.isEnabled(),
                f.getRolloutPercentage(),
                targetingRules,
                f.getVersion(),
                f.getCreatedAt(),
                f.getUpdatedAt()
        );
    }

}
