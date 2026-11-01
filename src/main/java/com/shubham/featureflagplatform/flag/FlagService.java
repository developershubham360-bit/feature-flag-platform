package com.shubham.featureflagplatform.flag;

import com.shubham.featureflagplatform.audit.AuditLog;
import com.shubham.featureflagplatform.audit.AuditLogRepository;
import com.shubham.featureflagplatform.cache.CachedFlag;
import com.shubham.featureflagplatform.cache.FlagCache;
import com.shubham.featureflagplatform.cache.LocalFlagCache;
import com.shubham.featureflagplatform.error.FlagNotFoundException;
import com.shubham.featureflagplatform.event.ChangeType;
import com.shubham.featureflagplatform.event.FlagChangedEvent;
import com.shubham.featureflagplatform.event.FlagSnapshot;
import com.shubham.featureflagplatform.event.RuleSnapshot;
import com.shubham.featureflagplatform.flag.dto.CreateFlagRequest;
import com.shubham.featureflagplatform.flag.dto.FlagDto;
import com.shubham.featureflagplatform.flag.dto.TargetingRuleDto;
import com.shubham.featureflagplatform.flag.dto.UpdateFlagRequest;
import com.shubham.featureflagplatform.outbox.FlagEventOutbox;
import com.shubham.featureflagplatform.targeting.TargetingRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FlagService {

    private final FlagRepository flagRepository;
    private final AuditLogRepository auditLogRepository;
    private final FlagCache flagCache;
    private final LocalFlagCache localFlagCache;
    private final FlagEventOutbox outbox;

    public FlagService(FlagRepository flagRepository, AuditLogRepository auditLogRepository,
                       FlagCache flagCache, LocalFlagCache localFlagCache, FlagEventOutbox outbox) {
        this.flagRepository = flagRepository;
        this.auditLogRepository = auditLogRepository;
        this.flagCache = flagCache;
        this.localFlagCache = localFlagCache;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<FlagDto> findAll() {
        return flagRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public FlagDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public FlagDto create(CreateFlagRequest req) {
        Flag flag = new Flag(req.key(), req.environment(), req.name(),
                req.description(), req.enabled(), req.rolloutPercentage());
        flag.setTargetingRules(toEntityRules(req.targetingRules()));
        Flag saved = flagRepository.save(flag);   
        audit(saved.getKey(), saved.getEnvironment(), "CREATE", "admin");
        publish(saved, ChangeType.CREATED);
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
        flag.setUpdatedAt(Instant.now());
        flagRepository.flush();
        audit(flag.getKey(), flag.getEnvironment(), "UPDATE", "admin");
        publish(flag, ChangeType.UPDATED);
        return toDto(flag);
    }

    @Transactional
    public void delete(Long id) {
        Flag flag = getOrThrow(id);
        flagRepository.delete(flag);              
        audit(flag.getKey(), flag.getEnvironment(), "DELETE", "admin");
        publish(flag, ChangeType.DELETED);
    }

    private void publish(Flag flag, ChangeType type) {
        FlagChangedEvent event = toEvent(flag, type);
        outbox.append(event);

        String env = flag.getEnvironment();
        String key = flag.getKey();
        CachedFlag local = CachedFlag.from(event);
        afterCommit(() -> {
            flagCache.evict(env, key);
            localFlagCache.merge(env, key, local);
        });
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private FlagChangedEvent toEvent(Flag flag, ChangeType type) {
        if (type == ChangeType.DELETED) {
            // Tombstone: version + 1 beats every earlier snapshot of this row.
            return new FlagChangedEvent(UUID.randomUUID(), flag.getId(), flag.getKey(),
                    flag.getEnvironment(), flag.getVersion() + 1, type, Instant.now(), null);
        }
        List<RuleSnapshot> rules = flag.getTargetingRules().stream()
                .map(r -> new RuleSnapshot(r.getAttribute(), r.getOperator(), r.getValue()))
                .toList();
        FlagSnapshot snapshot = new FlagSnapshot(flag.isEnabled(), flag.getRolloutPercentage(), rules);
        return new FlagChangedEvent(UUID.randomUUID(), flag.getId(), flag.getKey(),
                flag.getEnvironment(), flag.getVersion(), type, Instant.now(), snapshot);
    }

    private Flag getOrThrow(Long id) {
        return flagRepository.findById(id).orElseThrow(() -> new FlagNotFoundException(id));
    }

    private void audit(String key, String env, String action, String actor) {
        auditLogRepository.save(new AuditLog(key, env, action, actor));
    }

    private List<TargetingRule> toEntityRules(List<TargetingRuleDto> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream()
                .map(d -> new TargetingRule(d.attribute(), d.operator(), d.value()))
                .toList();
    }

    private FlagDto toDto(Flag f) {
        List<TargetingRuleDto> rules = f.getTargetingRules().stream()
                .map(r -> new TargetingRuleDto(r.getAttribute(), r.getOperator(), r.getValue()))
                .toList();
        return new FlagDto(f.getId(), f.getKey(), f.getEnvironment(), f.getName(),
                f.getDescription(), f.isEnabled(), f.getRolloutPercentage(), rules,
                f.getVersion(), f.getCreatedAt(), f.getUpdatedAt());
    }
}