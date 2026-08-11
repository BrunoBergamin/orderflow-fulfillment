package br.com.bergamin.fulfillment.domain.model;

/** Situacao da entrega de uma notificacao. */
public enum NotificationStatus {

    /** Aguardando a proxima janela de envio. */
    PENDING,
    /** Entregue ao parceiro. */
    SENT,
    /**
     * Esgotou as tentativas.
     *
     * <p>Nao vira {@code SENT} nem some: fica registrada para inspecao. Notificacao que
     * desaparece em silencio e o tipo de falha que so aparece quando o cliente liga.</p>
     */
    DEAD
}
