package com.example.localhostfacom.expense;

import com.example.localhostfacom.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private final ExpenseRepository expenses;

    public ExpenseService(ExpenseRepository expenses) {
        this.expenses = expenses;
    }

    public List<Expense> list() {
        return expenses.findAllByOrderByIncurredOnDesc();
    }

    @Transactional
    public Expense create(String description, BigDecimal amount, LocalDate incurredOn, UUID createdBy) {
        return expenses.save(Expense.create(
                description.trim(),
                amount,
                incurredOn == null ? LocalDate.now() : incurredOn,
                createdBy));
    }

    @Transactional
    public void delete(UUID id) {
        if (!expenses.existsById(id)) {
            throw ApiException.notFound("expense-not-found", "Expense not found");
        }
        expenses.deleteById(id);
    }

    public BigDecimal total() {
        return expenses.findAll().stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
    }
}
