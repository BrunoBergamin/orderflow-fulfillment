package br.com.bergamin.fulfillment.application.service;

import br.com.bergamin.fulfillment.application.port.out.NotificationRepositoryPort;
import br.com.bergamin.fulfillment.domain.exception.ResourceNotFoundException;
import br.com.bergamin.fulfillment.domain.model.Notification;
import br.com.bergamin.fulfillment.domain.model.NotificationStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Grava o desfecho de cada entrega em sua propria transacao.
 *
 * <p>Bean separado do despachante por dois motivos. O primeiro e tecnico:
 * {@code @Transactional} so vale quando a chamada passa pelo proxy do Spring, e uma
 * auto-invocacao seria ignorada em silencio. O segundo e de projeto:
 * {@code REQUIRES_NEW} garante que o resultado de uma notificacao seja gravado
 * independentemente das outras do lote.</p>
 */
@Component
public class NotificationOutcomeWriter {

    private final NotificationRepositoryPort notifications;
    private final Clock clock;

    public NotificationOutcomeWriter(NotificationRepositoryPort notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID notificationId) {
        Notification notification = load(notificationId);
        notification.markSent(clock.instant());
        notifications.save(notification);
    }

    /** @return a situacao em que a notificacao ficou (PENDING para nova tentativa, ou DEAD) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationStatus registerFailure(UUID notificationId, String error) {
        Notification notification = load(notificationId);
        notification.registerFailure(error, clock.instant());
        notifications.save(notification);
        return notification.getStatus();
    }

    private Notification load(UUID notificationId) {
        return notifications.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificacao", notificationId));
    }
}
