package br.com.bergamin.fulfillment.application.port.in;

import br.com.bergamin.fulfillment.domain.event.OrderEvent;

import java.util.UUID;

/** Caso de uso: reagir a um evento de pedido, uma unica vez. */
public interface ProcessOrderEventUseCase {

    Outcome process(Command command);

    /**
     * @param eventId identificador da ocorrencia, vindo do cabecalho {@code eventId} da
     *                mensagem. E a chave da idempotencia -- nao pode ser o id do pedido,
     *                que se repete a cada evento do mesmo pedido.
     */
    record Command(UUID eventId, OrderEvent event) {
    }

    enum Outcome {
        /** Aplicado a projecao e notificacao agendada. */
        PROCESSED,
        /** Reentrega do mesmo evento: ignorado sem efeito colateral. */
        DUPLICATE_IGNORED,
        /** Evento mais antigo que o estado atual: descartado para nao regredir a projecao. */
        STALE_IGNORED
    }
}
