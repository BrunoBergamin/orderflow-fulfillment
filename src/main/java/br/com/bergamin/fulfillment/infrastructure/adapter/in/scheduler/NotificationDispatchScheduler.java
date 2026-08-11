package br.com.bergamin.fulfillment.infrastructure.adapter.in.scheduler;

import br.com.bergamin.fulfillment.application.port.in.DispatchNotificationsUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada por tempo.
 *
 * <p>E so um gatilho: nao decide nada, apenas chama o caso de uso em intervalos. Isso deixa
 * o despacho testavel sem esperar o relogio -- os testes chamam o caso de uso direto.</p>
 */
@Component
@ConditionalOnProperty(name = "fulfillment.dispatch.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDispatchScheduler {

    private final DispatchNotificationsUseCase dispatchNotifications;

    public NotificationDispatchScheduler(DispatchNotificationsUseCase dispatchNotifications) {
        this.dispatchNotifications = dispatchNotifications;
    }

    @Scheduled(fixedDelayString = "${fulfillment.dispatch.poll-interval-ms:5000}")
    public void dispatchPendingNotifications() {
        dispatchNotifications.dispatchDue();
    }
}
