package com.example.localhostfacom.order;

public enum OrderStatus {
    PENDING,
    PAID,
    EXPIRED,
    CANCELED;

    /**
     * PAID is the only terminal state. EXPIRED and CANCELED merely mean the system
     * stopped waiting, so a payment that lands late is still accepted.
     */
    public boolean canTransitionToPaid() {
        return this != PAID;
    }
}
