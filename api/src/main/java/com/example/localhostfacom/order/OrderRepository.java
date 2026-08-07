package com.example.localhostfacom.order;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(UUID id);

    Optional<Order> findByProviderPaymentId(String providerPaymentId);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Order> findByStatusOrderByPaidAtDesc(OrderStatus status, Pageable pageable);

    /**
     * Orders the reconciler should re-check. Includes EXPIRED and CANCELED ones within
     * the grace window, because a payment that lands after the local deadline is still
     * real money that must reach the ledger.
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.status <> com.example.localhostfacom.order.OrderStatus.PAID
              AND o.providerPaymentId IS NOT NULL
              AND o.createdAt > :notBefore
            """)
    List<Order> findReconcilable(Instant notBefore);

    @Query("""
            SELECT o FROM Order o
            WHERE o.status = com.example.localhostfacom.order.OrderStatus.PENDING
              AND o.expiresAt < :now
            """)
    List<Order> findExpirable(Instant now);

    @Query("""
            SELECT COALESCE(SUM(o.total), 0) FROM Order o
            WHERE o.status = com.example.localhostfacom.order.OrderStatus.PAID
            """)
    java.math.BigDecimal sumPaidTotal();

    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.status = com.example.localhostfacom.order.OrderStatus.PAID
            """)
    long countPaid();

    @Query("""
            SELECT COALESCE(SUM(o.total), 0) FROM Order o
            WHERE o.status = com.example.localhostfacom.order.OrderStatus.PAID
              AND o.paidAt >= :since
            """)
    java.math.BigDecimal sumPaidSince(Instant since);

    @Query("""
            SELECT i.productName FROM OrderItem i
            WHERE i.order.status = com.example.localhostfacom.order.OrderStatus.PAID
            GROUP BY i.productName
            ORDER BY SUM(i.quantity) DESC
            """)
    List<String> findProductNamesByUnitsSold(org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT o FROM Order o
            WHERE o.status = com.example.localhostfacom.order.OrderStatus.PAID
              AND o.paidAt >= :since
            """)
    List<Order> findPaidSince(Instant since);
}
