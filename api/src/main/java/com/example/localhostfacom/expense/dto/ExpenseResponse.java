package com.example.localhostfacom.expense.dto;

import com.example.localhostfacom.expense.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(UUID id, String description, BigDecimal amount, LocalDate incurredOn) {

    public static ExpenseResponse of(Expense expense) {
        return new ExpenseResponse(
                expense.getId(), expense.getDescription(), expense.getAmount(), expense.getIncurredOn());
    }
}
