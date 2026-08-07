package com.example.localhostfacom.dashboard.dto;

import java.util.List;

public record TransactionPageResponse(List<TransactionResponse> content, int totalPages, long totalElements) {}
