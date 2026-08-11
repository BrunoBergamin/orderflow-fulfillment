package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity;

import br.com.bergamin.fulfillment.domain.model.NotificationStatus;
import br.com.bergamin.fulfillment.domain.model.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Tabela {@code notification}: fila de entregas com estado, tentativas e agendamento. */
@Entity
@Table(name = "notification")
public class NotificationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(length = 255)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Quando pode ser tentada de novo. Tambem funciona como lease enquanto esta em entrega. */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationJpaEntity() {
        // exigido pelo JPA
    }

    public NotificationJpaEntity(UUID id, UUID orderId, NotificationType type, String message,
                                 NotificationStatus status, int attempts, String lastError,
                                 Instant createdAt, Instant nextAttemptAt, Instant sentAt) {
        this.id = id;
        this.orderId = orderId;
        this.type = type;
        this.message = message;
        this.status = status;
        this.attempts = attempts;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.nextAttemptAt = nextAttemptAt;
        this.sentAt = sentAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }
}
