package br.com.bergamin.fulfillment.application.service;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.application.port.in.ManageFailedMessageUseCase;
import br.com.bergamin.fulfillment.application.port.out.FailedMessageRepositoryPort;
import br.com.bergamin.fulfillment.application.port.out.MessageRepublisherPort;
import br.com.bergamin.fulfillment.domain.exception.ResourceNotFoundException;
import br.com.bergamin.fulfillment.domain.model.FailedMessage;
import br.com.bergamin.fulfillment.domain.model.FailedMessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class FailedMessageService implements ManageFailedMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(FailedMessageService.class);

    private final FailedMessageRepositoryPort messages;
    private final MessageRepublisherPort republisher;
    private final Clock clock;

    public FailedMessageService(FailedMessageRepositoryPort messages,
                                MessageRepublisherPort republisher,
                                Clock clock) {
        this.messages = messages;
        this.republisher = republisher;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<FailedMessage> search(FailedMessageStatus status, PageQuery pageQuery) {
        return messages.search(status, pageQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public FailedMessage findById(UUID messageId) {
        return messages.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Mensagem", messageId));
    }

    /**
     * Reenvia primeiro, marca depois.
     *
     * <p>A ordem e deliberada. Marcando antes, uma falha no broker deixaria a mensagem como
     * "reprocessada" sem nunca ter saido -- e ela sumiria da lista de pendencias sem ter
     * sido resolvida. Do jeito atual, se o envio falhar a excecao sobe, a marcacao nao
     * acontece e a mensagem continua na fila esperando outra tentativa.</p>
     *
     * <p>O preco e o risco oposto: enviar e falhar ao marcar, gerando um reenvio a mais. Mas
     * aqui isso e inofensivo, porque o consumidor e idempotente pelo {@code eventId}.</p>
     */
    @Override
    @Transactional
    public FailedMessage reprocess(UUID messageId) {
        FailedMessage message = findById(messageId);

        // Valida antes de publicar. Sem esta linha, uma mensagem sem eventId sairia para o
        // broker e falharia do outro lado, em vez de ser recusada aqui com 409.
        message.ensureReprocessable();

        republisher.republish(message);
        message.markReprocessed(clock.instant());

        log.info("mensagem {} (evento {}) reenviada ao topico principal",
                messageId, message.getEventId());
        return messages.save(message);
    }

    @Override
    @Transactional
    public FailedMessage discard(UUID messageId, String reason) {
        FailedMessage message = findById(messageId);
        message.discard(reason, clock.instant());

        log.warn("mensagem {} descartada: {}", messageId, message.getResolutionNote());
        return messages.save(message);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        return messages.countByStatus(FailedMessageStatus.PENDING);
    }
}
