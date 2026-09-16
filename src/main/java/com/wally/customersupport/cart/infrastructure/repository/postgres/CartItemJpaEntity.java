package com.wally.customersupport.cart.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "cart_items", schema = "wcs")
public class CartItemJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartJpaEntity cart;

    @Column(nullable = false, length = 80)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItemJpaEntity() {
    }

    CartItemJpaEntity(CartJpaEntity cart, String sku, int quantity, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.cart = cart;
        this.sku = sku;
        this.quantity = quantity;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    void increment(int amount, Instant now) {
        quantity += amount;
        updatedAt = now;
    }

    void decrement(int amount, Instant now) {
        quantity -= amount;
        updatedAt = now;
    }
}
