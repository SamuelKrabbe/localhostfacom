package com.example.localhostfacom.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "settings")
public class Settings {

    @Id
    private Short id;

    @Column(name = "goal_target", nullable = false, precision = 12, scale = 2)
    private BigDecimal goalTarget;

    @Column(name = "crowdfunding_url")
    private String crowdfundingUrl;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Settings() {}

    public void update(BigDecimal goalTarget, String crowdfundingUrl) {
        this.goalTarget = goalTarget;
        this.crowdfundingUrl = crowdfundingUrl;
        this.updatedAt = Instant.now();
    }

    public Short getId() { return id; }
    public BigDecimal getGoalTarget() { return goalTarget; }
    public String getCrowdfundingUrl() { return crowdfundingUrl; }
    public Instant getUpdatedAt() { return updatedAt; }
}
