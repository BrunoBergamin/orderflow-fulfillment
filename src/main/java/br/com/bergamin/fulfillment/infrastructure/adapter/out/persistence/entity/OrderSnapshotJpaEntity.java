package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity;

import br.com.bergamin.fulfillment.domain.model.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tabela {@code order_snapshot}: a projecao de leitura mantida a partir dos eventos. */
@Entity
@Table(name = "order_snapshot")
public class OrderSnapshotJpaEntity {

    /** O id do pedido vem da origem; este servico nao gera identidade propria para ele. */
    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "payment_transaction_id", length = 100)
    private String paymentTransactionId;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    protected OrderSnapshotJpaEntity() {
        // exigido pelo JPA
    }

    public OrderSnapshotJpaEntity(UUID orderId, Instant firstSeenAt) {
        this.orderId = orderId;
        this.firstSeenAt = firstSeenAt;
        this.totalAmount = BigDecimal.ZERO;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public String getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(String paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(Instant lastEventAt) {
        this.lastEventAt = lastEventAt;
    }
}
