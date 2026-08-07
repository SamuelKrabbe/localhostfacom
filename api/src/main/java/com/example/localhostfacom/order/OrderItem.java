package com.example.localhostfacom.order;

import com.example.localhostfacom.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {}

    /**
     * Copies the product's name and price rather than referencing them, so a later
     * price change never rewrites what a past sale recorded.
     */
    public static OrderItem snapshotOf(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.id = UUID.randomUUID();
        item.productId = product.getId();
        item.productName = product.getName();
        item.unitPrice = product.getPrice();
        item.quantity = quantity;
        return item;
    }

    void assignTo(Order order) {
        this.order = order;
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
}
