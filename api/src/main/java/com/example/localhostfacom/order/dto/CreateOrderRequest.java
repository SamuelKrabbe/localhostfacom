package com.example.localhostfacom.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Carries quantities only. Prices are never accepted from the client — the total is
 * always recomputed from the database.
 */
public record CreateOrderRequest(@NotEmpty @Size(max = 50) @Valid List<Item> items) {

    public record Item(
            @NotNull UUID productId,
            @Min(1) @Max(99) int quantity) {}
}
