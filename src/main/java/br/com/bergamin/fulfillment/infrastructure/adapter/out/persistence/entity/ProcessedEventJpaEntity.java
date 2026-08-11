package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Tabela {@code processed_event}: os eventos que este servico ja aplicou.
 *
 * <p>O id do evento e a propria chave primaria -- a unicidade nao depende de nenhum codigo
 * estar correto, e uma garantia do banco.</p>
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /** Trace da requisicao que originou o evento no servico de pedidos. */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    protected ProcessedEventJpaEntity() {
        // exigido pelo JPA
    }

    public ProcessedEventJpaEntity(UUID eventId, String eventType, Instant processedAt, String traceId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
        this.traceId = traceId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getTraceId() {
        return traceId;
    }
}
