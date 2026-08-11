package br.com.bergamin.fulfillment.infrastructure.adapter.in.rest.dto;

import br.com.bergamin.fulfillment.domain.model.FailedMessage;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class FailedMessageDtos {

    private FailedMessageDtos() {
    }

    public record DiscardRequest(
            @Size(max = 500, message = "motivo deve ter no maximo 500 caracteres") String reason) {
    }

    public record SummaryResponse(long pending) {
    }

    public record Response(
            UUID id,
            UUID eventId,
            String eventType,
            String aggregateId,
            String payload,
            String errorMessage,
            String originalTopic,
            Integer originalPartition,
            Long originalOffset,
            String traceId,
            Instant receivedAt,
            String status,
            int reprocessCount,
            Instant resolvedAt,
            String resolutionNote,
            boolean canReprocess) {

        public static Response from(FailedMessage message) {
            return new Response(
                    message.getId(), message.getEventId(), message.getEventType(),
                    message.getAggregateId(), message.getPayload(), message.getErrorMessage(),
                    message.getOriginalTopic(), message.getOriginalPartition(), message.getOriginalOffset(),
                    message.getTraceId(), message.getReceivedAt(), message.getStatus().name(),
                    message.getReprocessCount(), message.getResolvedAt(), message.getResolutionNote(),
                    // Evita que a interface ofereca "reenviar" para algo que vai falhar igual.
                    message.canReprocess());
        }
    }
}
