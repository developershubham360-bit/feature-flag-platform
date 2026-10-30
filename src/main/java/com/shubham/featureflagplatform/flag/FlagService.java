package com.shubham.featureflagplatform.flag;

import com.shubham.featureflagplatform.audit.AuditLog;
import com.shubham.featureflagplatform.audit.AuditLogRepository;
import com.shubham.featureflagplatform.cache.FlagCache;
import com.shubham.featureflagplatform.error.FlagNotFoundException;
import com.shubham.featureflagplatform.flag.dto.CreateFlagRequest;
import com.shubham.featureflagplatform.flag.dto.FlagDto;
import com.shubham.featureflagplatform.flag.dto.TargetingRuleDto;
import com.shubham.featureflagplatform.flag.dto.UpdateFlagRequest;
import com.shubham.featureflagplatform.targeting.TargetingRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

@Service
public class FlagService {

    private final FlagRepository flagRepository;
    private final AuditLogRepository auditLogRepository;
    private final FlagCache flagCache;

    public FlagService(FlagRepository flagRepository, AuditLogRepository auditLogRepository,
                       FlagCache flagCache) {
        this.flagRepository = flagRepository;
        this.auditLogRepository = auditLogRepository;
        this.flagCache = flagCache;
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
        // Clears any negative ("not found") entry an earlier /evaluate may have cached,
        // so the new flag becomes visible immediately after commit.
        evictAfterCommit(saved.getEnvironment(), saved.getKey());
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
        // no explicit save() needed — dirty checking flushes on commit
        audit(flag.getKey(), flag.getEnvironment(), "UPDATE", "admin");
        evictAfterCommit(flag.getEnvironment(), flag.getKey());
        return toDto(flag);
    }

    @Transactional
    public void delete(Long id) {
        Flag flag = getOrThrow(id);
        flagRepository.delete(flag);
        audit(flag.getKey(), flag.getEnvironment(), "DELETE", "admin");
        evictAfterCommit(flag.getEnvironment(), flag.getKey());
    }

    /**
     * Evict the cache entry ONLY after this transaction commits.
     *
     * If we evicted inline (before commit), a concurrent /evaluate could miss the cache,
     * read the still-uncommitted OLD value from Postgres, and repopulate Redis with stale
     * data that then sticks until the TTL. Registering an afterCommit() callback guarantees
     * the eviction fires only once the new value is durable, so any later read repopulates
     * from fresh data.
     */
    private void evictAfterCommit(String environment, String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    flagCache.evict(environment, key);
                }
            });
        } else {
            // Defensive: no active transaction (e.g. called outside @Transactional) — evict now.
            flagCache.evict(environment, key);
        }
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