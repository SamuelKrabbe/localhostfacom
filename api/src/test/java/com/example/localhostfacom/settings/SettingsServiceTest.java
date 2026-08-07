package com.example.localhostfacom.settings;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.expense.ExpenseRepository;
import com.example.localhostfacom.expense.ExpenseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SettingsServiceTest {

    @Autowired private SettingsService settings;
    @Autowired private ExpenseService expenses;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private AdminRepository adminRepository;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        adminRepository.deleteAll();
        settings.update(new BigDecimal("2000.00"), null);
        adminId = adminRepository.save(Admin.create("owner@example.com", "hash")).getId();
    }

    @Test
    void updatesTheGoalAndCrowdfundingLink() {
        settings.update(new BigDecimal("3500.00"), "https://vakinha.example/sala");

        assertThat(settings.get().getGoalTarget()).isEqualByComparingTo("3500.00");
        assertThat(settings.get().getCrowdfundingUrl()).isEqualTo("https://vakinha.example/sala");
    }

    /**
     * The public dashboard divides by this value to draw the progress bar, so a zero
     * target would render Infinity.
     */
    @Test
    void refusesANonPositiveGoal() {
        assertThatThrownBy(() -> settings.update(BigDecimal.ZERO, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void sumsRecordedExpenses() {
        expenses.create("Café em grão", new BigDecimal("40.00"), LocalDate.now(), adminId);
        expenses.create("Copos", new BigDecimal("12.50"), LocalDate.now(), adminId);

        assertThat(expenses.total()).isEqualByComparingTo("52.50");
    }

    @Test
    void reportsZeroWhenNothingHasBeenSpent() {
        assertThat(expenses.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void deletesAnExpense() {
        var expense = expenses.create("Erro", new BigDecimal("5.00"), LocalDate.now(), adminId);

        expenses.delete(expense.getId());

        assertThat(expenses.total()).isEqualByComparingTo("0.00");
    }
}
