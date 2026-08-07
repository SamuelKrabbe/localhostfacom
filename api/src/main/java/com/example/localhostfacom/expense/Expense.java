package com.example.localhostfacom.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expense")
public class Expense {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "incurred_on", nullable = false)
    private LocalDate incurredOn;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Expense() {}

    public static Expense create(String description, BigDecimal amount, LocalDate incurredOn, UUID createdBy) {
        Expense expense = new Expense();
        expense.id = UUID.randomUUID();
        expense.description = description;
        expense.amount = amount;
        expense.incurredOn = incurredOn;
        expense.createdBy = createdBy;
        expense.createdAt = Instant.now();
        return expense;
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getIncurredOn() { return incurredOn; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
