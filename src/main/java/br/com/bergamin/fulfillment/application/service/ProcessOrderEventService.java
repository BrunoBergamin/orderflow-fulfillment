package br.com.bergamin.fulfillment.application.service;

import br.com.bergamin.fulfillment.application.port.in.ProcessOrderEventUseCase;
import br.com.bergamin.fulfillment.application.port.out.NotificationRepositoryPort;
import br.com.bergamin.fulfillment.application.port.out.OrderSnapshotCachePort;
import br.com.bergamin.fulfillment.application.port.out.OrderSnapshotRepositoryPort;
import br.com.bergamin.fulfillment.application.port.out.ProcessedEventPort;
import br.com.bergamin.fulfillment.domain.event.OrderEvent;
import br.com.bergamin.fulfillment.domain.model.Notification;
import br.com.bergamin.fulfillment.domain.model.NotificationType;
import br.com.bergamin.fulfillment.domain.model.OrderSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Reage a um evento de pedido -- uma vez, e só uma.
 *
 * <p>Tudo acontece na mesma transacao: marcar o evento como processado, atualizar a projecao
 * e agendar a notificacao. Se qualquer passo falhar, o evento volta a constar como nao
 * processado e a reentrega do Kafka o traz de novo. Marcar como processado em uma transacao
 * separada criaria o pior dos mundos: evento "consumido" sem que o efeito dele exista.</p>
 */
@Service
public class ProcessOrderEventService implements ProcessOrderEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessOrderEventService.class);

    private final ProcessedEventPort processedEvents;
    private final OrderSnapshotRepositoryPort snapshots;
    private final NotificationRepositoryPort notifications;
    private final OrderSnapshotCachePort cache;
    private final Clock clock;

    public ProcessOrderEventService(ProcessedEventPort processedEvents,
                                    OrderSnapshotRepositoryPort snapshots,
                                    NotificationRepositoryPort notifications,
                                    OrderSnapshotCachePort cache,
                                    Clock clock) {
        this.processedEvents = processedEvents;
        this.snapshots = snapshots;
        this.notifications = notifications;
        this.cache = cache;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Outcome process(Command command) {
        OrderEvent event = command.event();
        Instant now = clock.instant();

        if (!processedEvents.markProcessed(command.eventId(), event.type(), now)) {
            log.debug("evento {} ja processado, reentrega ignorada", command.eventId());
            return Outcome.DUPLICATE_IGNORED;
        }

        OrderSnapshot snapshot = snapshots.findById(event.orderId())
                .orElseGet(() -> OrderSnapshot.empty(event.orderId(), now));

        if (snapshot.isStale(event.occurredAt())) {
            log.warn("evento {} do pedido {} chegou atrasado ({} < {}) e foi descartado",
                    event.type(), event.orderId(), event.occurredAt(), snapshot.getLastEventAt());
            return Outcome.STALE_IGNORED;
        }

        snapshot.apply(event);
        snapshots.save(snapshot);
        cache.evictAfterCommit(event.orderId());

        NotificationType type = NotificationType.from(event);
        notifications.save(Notification.schedule(event.orderId(), type, type.description(), now));

        log.info("evento {} aplicado ao pedido {} (status agora: {})",
                event.type(), event.orderId(), snapshot.getStatus());
        return Outcome.PROCESSED;
    }
}
