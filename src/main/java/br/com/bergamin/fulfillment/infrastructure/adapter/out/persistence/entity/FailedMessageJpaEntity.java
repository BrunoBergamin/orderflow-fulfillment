package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity;

import br.com.bergamin.fulfillment.domain.model.FailedMessageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Tabela {@code failed_message}. */
@Entity
@Table(name = "failed_message")
public class FailedMessageJpaEntity {

    @Id
    private UUID id;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "aggregate_id", length = 80)
    private String aggregateId;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "original_topic", length = 120)
    private String originalTopic;

    @Column(name = "original_partition")
    private Integer originalPartition;

    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FailedMessageStatus status;

    @Column(name = "reprocess_count", nullable = false)
    private int reprocessCount;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    protected FailedMessageJpaEntity() {
        // exigido pelo JPA
    }

    public FailedMessageJpaEntity(UUID id, UUID eventId, String eventType, String aggregateId,
                                  String payload, String errorMessage, String originalTopic,
                                  Integer originalPartition, Long originalOffset, String traceId,
                                  Instant receivedAt, FailedMessageStatus status, int reprocessCount,
                                  Instant resolvedAt, String resolutionNote) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.originalTopic = originalTopic;
        this.originalPartition = originalPartition;
        this.originalOffset = originalOffset;
        this.traceId = traceId;
        this.receivedAt = receivedAt;
        this.status = status;
        this.reprocessCount = reprocessCount;
        this.resolvedAt = resolvedAt;
        this.resolutionNote = resolutionNote;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getOriginalTopic() {
        return originalTopic;
    }

    public Integer getOriginalPartition() {
        return originalPartition;
    }

    public Long getOriginalOffset() {
        return originalOffset;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public FailedMessageStatus getStatus() {
        return status;
    }

    public void setStatus(FailedMessageStatus status) {
        this.status = status;
    }

    public int getReprocessCount() {
        return reprocessCount;
    }

    public void setReprocessCount(int reprocessCount) {
        this.reprocessCount = reprocessCount;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }
}
