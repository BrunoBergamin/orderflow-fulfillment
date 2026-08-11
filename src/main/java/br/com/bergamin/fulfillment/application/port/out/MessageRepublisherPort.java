package br.com.bergamin.fulfillment.application.port.out;

import br.com.bergamin.fulfillment.domain.model.FailedMessage;

/** Porta que devolve uma mensagem da DLQ ao topico principal. */
public interface MessageRepublisherPort {

    /**
     * @throws RuntimeException se o broker recusar; nesse caso a mensagem continua PENDING
     *         e pode ser tentada de novo
     */
    void republish(FailedMessage message);
}
