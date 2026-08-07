package com.example.localhostfacom.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String slug;

    public ApiException(HttpStatus status, String slug, String detail) {
        super(detail);
        this.status = status;
        this.slug = slug;
    }

    public static ApiException notFound(String slug, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, slug, detail);
    }

    public static ApiException badRequest(String slug, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, slug, detail);
    }

    public static ApiException conflict(String slug, String detail) {
        return new ApiException(HttpStatus.CONFLICT, slug, detail);
    }

    public static ApiException forbidden(String slug, String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, slug, detail);
    }

    public static ApiException badGateway(String slug, String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, slug, detail);
    }

    public static ApiException tooManyRequests(String slug, String detail) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, slug, detail);
    }

    public HttpStatus getStatus() { return status; }
    public String getSlug() { return slug; }
}
