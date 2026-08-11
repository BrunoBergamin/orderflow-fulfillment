package br.com.bergamin.fulfillment.domain.model;

/** Situacao do pedido na projecao mantida por este servico. */
public enum OrderStatus {

    PENDING,
    PAID,
    CANCELLED,
    PAYMENT_FAILED;

    /**
     * Traduz o cancelamento vindo do produtor.
     *
     * <p>O produtor publica um unico {@code Order.Cancelled} com a marca
     * {@code paymentDeclined}. A distincao importa para o pos-venda: desistencia do cliente
     * e recusa do cartao pedem mensagens e acoes diferentes.</p>
     */
    public static OrderStatus fromCancellation(boolean paymentDeclined) {
        return paymentDeclined ? PAYMENT_FAILED : CANCELLED;
    }
}
