package br.com.bergamin.fulfillment.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma entrega pendente para o parceiro externo.
 *
 * <p>A politica de retentativa mora aqui, no dominio, e nao espalhada em anotacao de
 * framework: quantas tentativas, quanto esperar entre elas e quando desistir sao decisoes
 * de negocio, testaveis sem subir Spring nem esperar em relogio de verdade.</p>
 *
 * <p>O intervalo cresce exponencialmente ({@code base * 2^tentativas}, com teto). Retentar
 * de imediato contra um parceiro que ja esta sobrecarregado so aumenta a fila dele -- o
 * backoff da tempo de o outro lado se recuperar.</p>
 */
public class Notification {

    public static final int MAX_ATTEMPTS = 5;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

    private final UUID id;
    private final UUID orderId;
    private final NotificationType type;
    private final String message;
    private NotificationStatus status;
    private int attempts;
    private String lastError;
    private final Instant createdAt;
    private Instant nextAttemptAt;
    private Instant sentAt;

    private Notification(UUID id, UUID orderId, NotificationType type, String message,
                         NotificationStatus status, int attempts, String lastError,
                         Instant createdAt, Instant nextAttemptAt, Instant sentAt) {
        this.id = Objects.requireNonNull(id, "id e obrigatorio");
        this.orderId = Objects.requireNonNull(orderId, "orderId e obrigatorio");
        this.type = Objects.requireNonNull(type, "type e obrigatorio");
        this.message = message;
        this.status = status;
        this.attempts = attempts;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.nextAttemptAt = nextAttemptAt;
        this.sentAt = sentAt;
    }

    public static Notification schedule(UUID orderId, NotificationType type, String message, Instant now) {
        return new Notification(UUID.randomUUID(), orderId, type, message,
                NotificationStatus.PENDING, 0, null, now, now, null);
    }

    public static Notification restore(UUID id, UUID orderId, NotificationType type, String message,
                                       NotificationStatus status, int attempts, String lastError,
                                       Instant createdAt, Instant nextAttemptAt, Instant sentAt) {
        return new Notification(id, orderId, type, message, status, attempts,
                lastError, createdAt, nextAttemptAt, sentAt);
    }

    /** Entregue com sucesso. */
    public void markSent(Instant now) {
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.lastError = null;
        this.nextAttemptAt = null;
    }

    /**
     * Registra a falha e agenda a proxima tentativa -- ou desiste, se esgotou o limite.
     */
    public void registerFailure(String error, Instant now) {
        this.attempts++;
        this.lastError = truncate(error);

        if (this.attempts >= MAX_ATTEMPTS) {
            this.status = NotificationStatus.DEAD;
            this.nextAttemptAt = null;
            return;
        }
        this.status = NotificationStatus.PENDING;
        this.nextAttemptAt = now.plus(backoffFor(this.attempts));
    }

    /** Pronta para ser tentada agora. */
    public boolean isDue(Instant now) {
        return status == NotificationStatus.PENDING
                && nextAttemptAt != null
                && !nextAttemptAt.isAfter(now);
    }

    public boolean isDead() {
        return status == NotificationStatus.DEAD;
    }

    static Duration backoffFor(int attempts) {
        Duration backoff = BASE_BACKOFF.multipliedBy(1L << (attempts - 1));
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
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

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
