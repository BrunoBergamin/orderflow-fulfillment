package br.com.bergamin.fulfillment.infrastructure.adapter.in.rest.dto;

import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.domain.model.Notification;
import br.com.bergamin.fulfillment.domain.model.OrderSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Contratos HTTP da API de leitura. */
public final class FulfillmentDtos {

    private FulfillmentDtos() {
    }

    public record OrderSnapshotResponse(
            UUID orderId,
            UUID customerId,
            String status,
            BigDecimal totalAmount,
            int itemCount,
            String paymentTransactionId,
            String statusReason,
            Instant firstSeenAt,
            Instant lastEventAt) {

        public static OrderSnapshotResponse from(OrderSnapshot snapshot) {
            return new OrderSnapshotResponse(
                    snapshot.getOrderId(),
                    snapshot.getCustomerId(),
                    snapshot.getStatus() == null ? null : snapshot.getStatus().name(),
                    snapshot.getTotalAmount(),
                    snapshot.getItemCount(),
                    snapshot.getPaymentTransactionId(),
                    snapshot.getStatusReason(),
                    snapshot.getFirstSeenAt(),
                    snapshot.getLastEventAt());
        }
    }

    public record NotificationResponse(
            UUID id,
            UUID orderId,
            String type,
            String message,
            String status,
            int attempts,
            String lastError,
            Instant createdAt,
            Instant nextAttemptAt,
            Instant sentAt) {

        public static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getOrderId(),
                    notification.getType().name(),
                    notification.getMessage(),
                    notification.getStatus().name(),
                    notification.getAttempts(),
                    notification.getLastError(),
                    notification.getCreatedAt(),
                    notification.getNextAttemptAt(),
                    notification.getSentAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size,
                                  long totalElements, int totalPages, boolean hasNext) {

        public static <D, R> PageResponse<R> from(PagedResult<D> result, Function<D, R> mapper) {
            return new PageResponse<>(
                    result.content().stream().map(mapper).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages(),
                    result.hasNext());
        }
    }
}
