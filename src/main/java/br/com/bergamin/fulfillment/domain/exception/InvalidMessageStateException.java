package br.com.bergamin.fulfillment.domain.exception;

import br.com.bergamin.fulfillment.domain.model.FailedMessageStatus;

import java.util.UUID;

/** Operacao nao permitida para a situacao atual da mensagem. Vira HTTP 409. */
public class InvalidMessageStateException extends RuntimeException {

    private final FailedMessageStatus currentStatus;

    public InvalidMessageStateException(UUID messageId, FailedMessageStatus currentStatus, String reason) {
        super("mensagem %s esta %s: %s".formatted(messageId, currentStatus, reason));
        this.currentStatus = currentStatus;
    }

    public FailedMessageStatus getCurrentStatus() {
        return currentStatus;
    }
}
