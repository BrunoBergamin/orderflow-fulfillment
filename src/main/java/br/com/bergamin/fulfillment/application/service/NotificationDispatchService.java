package br.com.bergamin.fulfillment.application.service;

import br.com.bergamin.fulfillment.application.port.in.DispatchNotificationsUseCase;
import br.com.bergamin.fulfillment.application.port.out.NotificationRepositoryPort;
import br.com.bergamin.fulfillment.application.port.out.NotificationSenderPort;
import br.com.bergamin.fulfillment.domain.model.Notification;
import br.com.bergamin.fulfillment.domain.model.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Entrega as notificacoes vencidas.
 *
 * <p>Como no servico de pedidos, a chamada externa acontece <b>fora</b> de transacao: cada
 * entrega e uma ida a rede, e segurar uma conexao do pool durante um lote inteiro esgotaria
 * o pool no primeiro dia em que o parceiro ficar lento. O resultado de cada uma e gravado
 * por {@link NotificationOutcomeWriter} em transacoes curtas e independentes. Uma entrega
 * que falha nao desfaz as que deram certo no mesmo lote.</p>
 */
@Service
public class NotificationDispatchService implements DispatchNotificationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationRepositoryPort notifications;
    private final NotificationSenderPort sender;
    private final NotificationOutcomeWriter outcomeWriter;
    private final int batchSize;
    private final java.time.Clock clock;

    public NotificationDispatchService(NotificationRepositoryPort notifications,
                                       NotificationSenderPort sender,
                                       NotificationOutcomeWriter outcomeWriter,
                                       java.time.Clock clock,
                                       @Value("${fulfillment.dispatch.batch-size:50}") int batchSize) {
        this.notifications = notifications;
        this.sender = sender;
        this.outcomeWriter = outcomeWriter;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Override
    public Report dispatchDue() {
        List<Notification> due = notifications.lockDueBatch(clock.instant(), batchSize);
        if (due.isEmpty()) {
            return Report.empty();
        }

        int sent = 0;
        int retryScheduled = 0;
        int dead = 0;

        for (Notification notification : due) {
            try {
                sender.deliver(notification);
                outcomeWriter.markSent(notification.getId());
                sent++;
            } catch (RuntimeException e) {
                NotificationStatus resulting = outcomeWriter.registerFailure(notification.getId(), e.getMessage());
                if (resulting == NotificationStatus.DEAD) {
                    dead++;
                    log.error("notificacao {} do pedido {} esgotou as tentativas: {}",
                            notification.getId(), notification.getOrderId(), e.getMessage());
                } else {
                    retryScheduled++;
                    log.warn("falha ao entregar a notificacao {}, reagendada: {}",
                            notification.getId(), e.getMessage());
                }
            }
        }

        log.info("lote de notificacoes: {} tentadas, {} entregues, {} reagendadas, {} descartadas",
                due.size(), sent, retryScheduled, dead);
        return new Report(due.size(), sent, retryScheduled, dead);
    }
}
