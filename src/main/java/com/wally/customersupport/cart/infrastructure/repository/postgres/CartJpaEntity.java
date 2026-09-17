package com.wally.customersupport.cart.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.cart.domain.model.CartStatus;
import com.wally.customersupport.conversation.domain.model.Channel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "carts", schema = "wcs")
public class CartJpaEntity {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false, unique = true)
    private UUID conversationId;

    @Column(name = "actor_key", nullable = false, length = 128)
    private String actorKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Channel channel;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CartStatus status;

    @Column(nullable = false)
    private long version;

    @Column(name = "checkout_order_id")
    private UUID checkoutOrderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion;

    @OneToMany(mappedBy = "cart", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItemJpaEntity> items = new ArrayList<>();

    protected CartJpaEntity() {
    }

    public CartJpaEntity(
            UUID id,
            UUID conversationId,
            String actorKey,
            Channel channel,
            String currency,
            Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.actorKey = actorKey;
        this.channel = channel;
        this.currency = currency;
        this.status = CartStatus.ACTIVE;
        this.version = 0;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void addOrIncrement(String sku, int quantity, Instant now) {
        requireActive();
        if (quantity < 1 || quantity > 100) {
            throw new CartStateException("INVALID_QUANTITY");
        }
        CartItemJpaEntity existing = items.stream()
                .filter(item -> item.getSku().equalsIgnoreCase(sku))
                .findFirst()
                .orElse(null);
        if (existing == null) {
            items.add(new CartItemJpaEntity(this, sku, quantity, now));
        } else {
            int totalQuantity = existing.getQuantity() + quantity;
            if (totalQuantity > 100) {
                throw new CartStateException("INVALID_QUANTITY");
            }
            existing.increment(quantity, now);
        }
        touch(now);
    }

    public void remove(String sku, int quantity, Instant now) {
        requireActive();
        CartItemJpaEntity existing = items.stream()
                .filter(item -> item.getSku().equalsIgnoreCase(sku))
                .findFirst()
                .orElseThrow(() -> new CartStateException("ITEM_NOT_IN_CART"));
        if (quantity < 1 || quantity > 100) {
            throw new CartStateException("INVALID_QUANTITY");
        }
        if (quantity >= existing.getQuantity()) {
            items.remove(existing);
        } else {
            existing.decrement(quantity, now);
        }
        touch(now);
    }

    public void clear(Instant now) {
        requireActive();
        items.clear();
        touch(now);
    }

    /** Resets the cart and invalidates its previous checkout version. */
    public void reset(Instant now) {
        items.clear();
        status = CartStatus.ACTIVE;
        checkoutOrderId = null;
        touch(now);
    }

    public void markCheckoutPending(UUID orderId, Instant now) {
        if (orderId == null) {
            throw new CartStateException("ORDER_REQUIRED");
        }
        this.status = CartStatus.CHECKOUT_PENDING;
        this.checkoutOrderId = orderId;
        this.updatedAt = now;
    }

    public void cancelCheckout(Instant now) {
        if (status == CartStatus.CHECKOUT_PENDING) {
            this.status = CartStatus.ACTIVE;
            this.checkoutOrderId = null;
            touch(now);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public String getActorKey() {
        return actorKey;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getCurrency() {
        return currency;
    }

    public CartStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public UUID getCheckoutOrderId() {
        return checkoutOrderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<CartItemJpaEntity> getItems() {
        return List.copyOf(items);
    }

    private void requireActive() {
        if (status != CartStatus.ACTIVE) {
            throw new CartStateException("CART_CHECKOUT_PENDING");
        }
    }

    private void touch(Instant now) {
        version++;
        updatedAt = now;
    }

    public static class CartStateException extends RuntimeException {
        public CartStateException(String code) {
            super(code);
        }
    }
}
