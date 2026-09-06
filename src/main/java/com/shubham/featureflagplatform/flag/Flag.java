package com.shubham.featureflagplatform.flag;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.shubham.featureflagplatform.targeting.TargetingRule;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity 
@Table (
    name = "flags",
    uniqueConstraints = @UniqueConstraint(columnNames = {"flag_key", "environment"})
)
public class Flag {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column (name = "flag_key", nullable = false)
    private String key;

    @Column (nullable = false)
    private String environment;

    private String name;
    private String description;

    @Column (nullable = false)
    private boolean enabled;

    @Column (name = "rollout_percentage", nullable = false)
    private int rolloutPercentage;

    @ElementCollection (fetch = FetchType.EAGER)
    @CollectionTable (
        name = "targeting_rules",
        joinColumns = @JoinColumn(name = "flag_id")
    )
    private List<TargetingRule> targetingRules = new ArrayList<>();

    @Column (nullable = false)
    private long version;

    @Column (nullable = false, updatable = false)
    private Instant createdAt;
 
    @Column (nullable = false)
    private Instant updatedAt;

    protected Flag() {}

    public Flag(String key, String environment, String name, String description, boolean enabled, int rolloutPercentage) {
        this.key = key;
        this.environment = environment;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.rolloutPercentage = rolloutPercentage;
        this.version = 1;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getKey() { return key; }
    public String getEnvironment() { return environment; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public int getRolloutPercentage() { return rolloutPercentage; }
    public List<TargetingRule> getTargetingRules() { return targetingRules; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) {this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setRolloutPercentage(int rolloutPercentage) { this.rolloutPercentage = rolloutPercentage; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setVersion(long version) { this.version = version; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public void setTargetingRules(List<TargetingRule> targetingRules) {
        this.targetingRules.clear();
        if (targetingRules != null) {
            this.targetingRules.addAll(targetingRules);
        }
    }

}
