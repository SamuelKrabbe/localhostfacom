package com.example.localhostfacom.auth.dto;

import java.time.Instant;

public record LoginResponse(String token, String email, Instant expiresAt) {}
