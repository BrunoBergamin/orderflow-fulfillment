package br.com.bergamin.fulfillment.domain.model;

import br.com.bergamin.fulfillment.domain.event.OrderEvent;

/** O que o parceiro de logistica precisa saber sobre o pedido. */
public enum NotificationType {

    ORDER_RECEIVED("Pedido recebido e estoque reservado"),
    PAYMENT_CONFIRMED("Pagamento aprovado, pedido liberado para separacao"),
    ORDER_CANCELLED("Pedido cancelado pelo cliente"),
    PAYMENT_DECLINED("Pagamento recusado, pedido encerrado");

    private final String description;

    NotificationType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** Traduz o fato ocorrido no pedido para a notificacao correspondente. */
    public static NotificationType from(OrderEvent event) {
        return switch (event) {
            case OrderEvent.OrderPlacedEvent ignored -> ORDER_RECEIVED;
            case OrderEvent.OrderPaidEvent ignored -> PAYMENT_CONFIRMED;
            case OrderEvent.OrderCancelledEvent cancelled ->
                    cancelled.paymentDeclined() ? PAYMENT_DECLINED : ORDER_CANCELLED;
        };
    }
}
