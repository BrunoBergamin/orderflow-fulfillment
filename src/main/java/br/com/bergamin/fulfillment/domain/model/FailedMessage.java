package br.com.bergamin.fulfillment.domain.model;

import br.com.bergamin.fulfillment.domain.exception.InvalidMessageStateException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma mensagem que a aplicacao nao conseguiu processar e foi para a DLQ.
 *
 * <p>Mandar para a fila de mensagens mortas resolve metade do problema: desentope a
 * particao. A outra metade e o que este agregado cobre -- a mensagem fica visivel,
 * consultavel e com um caminho de volta. DLQ sem reprocessamento e so um lugar mais
 * organizado para perder dado.</p>
 */
public class FailedMessage {

    private final UUID id;
    private final UUID eventId;
    private final String eventType;
    private final String aggregateId;
    private final String payload;
    private final String errorMessage;
    private final String originalTopic;
    private final Integer originalPartition;
    private final Long originalOffset;
    private final String traceId;
    private final Instant receivedAt;

    private FailedMessageStatus status;
    private int reprocessCount;
    private Instant resolvedAt;
    private String resolutionNote;

    private FailedMessage(UUID id, UUID eventId, String eventType, String aggregateId, String payload,
                          String errorMessage, String originalTopic, Integer originalPartition,
                          Long originalOffset, String traceId, Instant receivedAt,
                          FailedMessageStatus status, int reprocessCount,
                          Instant resolvedAt, String resolutionNote) {
        this.id = Objects.requireNonNull(id, "id e obrigatorio");
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.originalTopic = originalTopic;
        this.originalPartition = originalPartition;
        this.originalOffset = originalOffset;
        this.traceId = traceId;
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt e obrigatorio");
        this.status = Objects.requireNonNull(status, "status e obrigatorio");
        this.reprocessCount = reprocessCount;
        this.resolvedAt = resolvedAt;
        this.resolutionNote = resolutionNote;
    }

    public static FailedMessage received(UUID eventId, String eventType, String aggregateId,
                                         String payload, String errorMessage, String originalTopic,
                                         Integer originalPartition, Long originalOffset,
                                         String traceId, Instant receivedAt) {
        return new FailedMessage(UUID.randomUUID(), eventId, eventType, aggregateId, payload,
                errorMessage, originalTopic, originalPartition, originalOffset, traceId,
                receivedAt, FailedMessageStatus.PENDING, 0, null, null);
    }

    public static FailedMessage restore(UUID id, UUID eventId, String eventType, String aggregateId,
                                        String payload, String errorMessage, String originalTopic,
                                        Integer originalPartition, Long originalOffset, String traceId,
                                        Instant receivedAt, FailedMessageStatus status,
                                        int reprocessCount, Instant resolvedAt, String resolutionNote) {
        return new FailedMessage(id, eventId, eventType, aggregateId, payload, errorMessage,
                originalTopic, originalPartition, originalOffset, traceId, receivedAt,
                status, reprocessCount, resolvedAt, resolutionNote);
    }

    /**
     * Reenviar so faz sentido se a mensagem tiver identificacao.
     *
     * <p>Cabecalho {@code eventId} ausente e uma das causas de a mensagem ter caido aqui --
     * e reenviar sem ele produziria exatamente a mesma falha. Nesse caso o unico caminho e
     * o descarte com motivo, e o agregado nao deixa a operacao errada acontecer.</p>
     */
    public boolean canReprocess() {
        return status == FailedMessageStatus.PENDING && eventId != null && eventType != null;
    }

    /**
     * Recusa a operacao antes que ela comece.
     *
     * <p>Existe separado de {@link #markReprocessed} porque a validacao precisa acontecer
     * <b>antes</b> de a mensagem sair para o broker -- validar so na marcacao deixaria uma
     * mensagem sem identificacao ser publicada e falhar de novo do outro lado.</p>
     */
    public void ensureReprocessable() {
        if (!canReprocess()) {
            throw new InvalidMessageStateException(id, status,
                    eventId == null || eventType == null
                            ? "mensagem sem eventId ou eventType nao pode ser reenviada"
                            : "apenas mensagens pendentes podem ser reenviadas");
        }
    }

    public void markReprocessed(Instant now) {
        ensureReprocessable();
        this.status = FailedMessageStatus.REPROCESSED;
        this.reprocessCount++;
        this.resolvedAt = now;
    }

    public void discard(String reason, Instant now) {
        if (status != FailedMessageStatus.PENDING) {
            throw new InvalidMessageStateException(id, status,
                    "apenas mensagens pendentes podem ser descartadas");
        }
        this.status = FailedMessageStatus.DISCARDED;
        this.resolvedAt = now;
        this.resolutionNote = reason == null || reason.isBlank() ? "descartada sem motivo informado" : reason;
    }

    public boolean isPending() {
        return status == FailedMessageStatus.PENDING;
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

    public int getReprocessCount() {
        return reprocessCount;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }
}
