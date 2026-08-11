package br.com.bergamin.fulfillment.application.port.out;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.application.port.in.FindNotificationUseCase;
import br.com.bergamin.fulfillment.domain.model.Notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de saida das notificacoes. */
public interface NotificationRepositoryPort {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID notificationId);

    /**
     * Reserva o proximo lote de notificacoes vencidas.
     *
     * <p>Como no relay de outbox do servico de pedidos, o lote e trancado com
     * {@code SKIP LOCKED}: varias instancias podem despachar em paralelo sem entregar a
     * mesma notificacao duas vezes nem formar fila numa trava.</p>
     */
    List<Notification> lockDueBatch(Instant now, int batchSize);

    PagedResult<Notification> search(FindNotificationUseCase.Filter filter, PageQuery pageQuery);
}
