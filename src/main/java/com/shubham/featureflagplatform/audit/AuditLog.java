package com.shubham.featureflagplatform.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flag_key", nullable = false)
    private String flagKey;

    @Column(nullable = false)
    private String environment;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false, updatable = false) 
    private Instant timestamp;

    protected AuditLog() {}

    public AuditLog(String flagKey, String environment, String action, String actor) {
        this.flagKey = flagKey;
        this.environment = environment;
        this.action = action;
        this.actor = actor;
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; } 
    public String getFlagKey() { return flagKey; }
    public String getEnvironment() { return environment; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public Instant getTimestamp() { return timestamp; }
}
