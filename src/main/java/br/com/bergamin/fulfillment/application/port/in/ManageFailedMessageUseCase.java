package br.com.bergamin.fulfillment.application.port.in;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.domain.model.FailedMessage;
import br.com.bergamin.fulfillment.domain.model.FailedMessageStatus;

import java.util.UUID;

/** Caso de uso: operar a fila de mensagens mortas. */
public interface ManageFailedMessageUseCase {

    PagedResult<FailedMessage> search(FailedMessageStatus status, PageQuery pageQuery);

    FailedMessage findById(UUID messageId);

    /**
     * Reenvia a mensagem ao topico principal.
     *
     * <p>Reenviada com o mesmo {@code eventId}, entao se a falha original tiver acontecido
     * <i>depois</i> de o evento ja ter sido aplicado, a guarda de idempotencia reconhece a
     * duplicata e nada acontece duas vezes. O reprocessamento se apoia na mesma protecao que
     * ja existia para reentrega do Kafka.</p>
     */
    FailedMessage reprocess(UUID messageId);

    /** Descarta com motivo. Caminho para mensagem que reenviar nao resolveria. */
    FailedMessage discard(UUID messageId, String reason);

    long countPending();
}
