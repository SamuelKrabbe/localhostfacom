package com.example.localhostfacom.image.dto;

import java.util.UUID;

public record ImageResponse(UUID id, String url, int width, int height) {}
