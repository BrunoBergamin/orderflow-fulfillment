package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.mapper;

import br.com.bergamin.fulfillment.domain.model.Notification;
import br.com.bergamin.fulfillment.domain.model.OrderSnapshot;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity.NotificationJpaEntity;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity.OrderSnapshotJpaEntity;
import org.springframework.stereotype.Component;

/** Traducao entre o modelo de dominio e as linhas das tabelas. */
@Component
public class PersistenceMapper {

    public OrderSnapshot toDomain(OrderSnapshotJpaEntity entity) {
        return OrderSnapshot.restore(
                entity.getOrderId(),
                entity.getCustomerId(),
                entity.getStatus(),
                entity.getTotalAmount(),
                entity.getItemCount(),
                entity.getPaymentTransactionId(),
                entity.getStatusReason(),
                entity.getFirstSeenAt(),
                entity.getLastEventAt());
    }

    /** Copia o estado do dominio para a entidade gerenciada. */
    public void copyToEntity(OrderSnapshot snapshot, OrderSnapshotJpaEntity entity) {
        entity.setCustomerId(snapshot.getCustomerId());
        entity.setStatus(snapshot.getStatus());
        entity.setTotalAmount(snapshot.getTotalAmount());
        entity.setItemCount(snapshot.getItemCount());
        entity.setPaymentTransactionId(snapshot.getPaymentTransactionId());
        entity.setStatusReason(snapshot.getStatusReason());
        entity.setLastEventAt(snapshot.getLastEventAt());
    }

    public Notification toDomain(NotificationJpaEntity entity) {
        return Notification.restore(
                entity.getId(),
                entity.getOrderId(),
                entity.getType(),
                entity.getMessage(),
                entity.getStatus(),
                entity.getAttempts(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getNextAttemptAt(),
                entity.getSentAt());
    }

    public NotificationJpaEntity toEntity(Notification notification) {
        return new NotificationJpaEntity(
                notification.getId(),
                notification.getOrderId(),
                notification.getType(),
                notification.getMessage(),
                notification.getStatus(),
                notification.getAttempts(),
                notification.getLastError(),
                notification.getCreatedAt(),
                notification.getNextAttemptAt(),
                notification.getSentAt());
    }

    public void copyToEntity(Notification notification, NotificationJpaEntity entity) {
        entity.setStatus(notification.getStatus());
        entity.setAttempts(notification.getAttempts());
        entity.setLastError(notification.getLastError());
        entity.setNextAttemptAt(notification.getNextAttemptAt());
        entity.setSentAt(notification.getSentAt());
    }
}
