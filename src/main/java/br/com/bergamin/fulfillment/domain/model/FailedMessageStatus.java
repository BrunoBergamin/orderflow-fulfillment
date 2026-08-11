package br.com.bergamin.fulfillment.domain.model;

/** Situacao de uma mensagem que caiu na DLQ. */
public enum FailedMessageStatus {

    /** Aguardando decisao humana. */
    PENDING,
    /** Reenviada ao topico principal. Se falhar de novo, entra como uma linha nova. */
    REPROCESSED,
    /** Descartada conscientemente, com motivo registrado. */
    DISCARDED
}
