package br.com.bergamin.fulfillment.domain.model;

import br.com.bergamin.fulfillment.domain.event.OrderEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Projecao de leitura do pedido, construida a partir dos eventos (CQRS enxuto).
 *
 * <p>Este servico nunca consulta o banco do servico de pedidos. Ele monta a propria visao a
 * partir do que ouviu, o que o deixa responder consultas mesmo com o produtor fora do ar --
 * e e o que permite os dois evoluirem sem combinar schema.</p>
 */
public class OrderSnapshot {

    private final UUID orderId;
    private UUID customerId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private int itemCount;
    private String paymentTransactionId;
    private String statusReason;
    private final Instant firstSeenAt;
    private Instant lastEventAt;

    private OrderSnapshot(UUID orderId, Instant firstSeenAt) {
        this.orderId = Objects.requireNonNull(orderId, "orderId e obrigatorio");
        this.firstSeenAt = Objects.requireNonNull(firstSeenAt, "firstSeenAt e obrigatorio");
        this.totalAmount = BigDecimal.ZERO;
    }

    /**
     * Cria uma projecao vazia.
     *
     * <p>Nasce sem estado porque um {@code Order.Paid} pode chegar antes do
     * {@code Order.Placed} correspondente -- por exemplo, se o {@code Placed} tiver ido
     * para a DLQ e sido reprocessado depois. Melhor registrar o que se sabe do que
     * descartar o evento.</p>
     */
    public static OrderSnapshot empty(UUID orderId, Instant firstSeenAt) {
        return new OrderSnapshot(orderId, firstSeenAt);
    }

    public static OrderSnapshot restore(UUID orderId, UUID customerId, OrderStatus status,
                                        BigDecimal totalAmount, int itemCount,
                                        String paymentTransactionId, String statusReason,
                                        Instant firstSeenAt, Instant lastEventAt) {
        OrderSnapshot snapshot = new OrderSnapshot(orderId, firstSeenAt);
        snapshot.customerId = customerId;
        snapshot.status = status;
        snapshot.totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        snapshot.itemCount = itemCount;
        snapshot.paymentTransactionId = paymentTransactionId;
        snapshot.statusReason = statusReason;
        snapshot.lastEventAt = lastEventAt;
        return snapshot;
    }

    /**
     * Indica que o evento e mais velho do que o ultimo ja aplicado.
     *
     * <p>O Kafka garante ordem dentro da particao, e a chave e o id do pedido -- entao, no
     * caminho feliz, os eventos de um pedido chegam em ordem. O caminho infeliz existe: uma
     * mensagem que falhou, foi para a DLQ e voltou a ser processada chega depois das que
     * vieram atras dela. Sem esta guarda, um {@code Placed} reprocessado sobrescreveria um
     * pedido que ja esta pago.</p>
     */
    public boolean isStale(Instant eventOccurredAt) {
        return lastEventAt != null && eventOccurredAt.isBefore(lastEventAt);
    }

    /** Aplica o evento a projecao. O {@code switch} e exaustivo por ser um tipo selado. */
    public void apply(OrderEvent event) {
        switch (event) {
            case OrderEvent.OrderPlacedEvent placed -> {
                this.customerId = placed.customerId();
                this.totalAmount = placed.total();
                this.itemCount = placed.itemCount();
                // Nao rebaixa o status: se o pagamento ja foi visto, o pedido continua pago.
                if (this.status == null) {
                    this.status = OrderStatus.PENDING;
                }
            }
            case OrderEvent.OrderPaidEvent paid -> {
                this.status = OrderStatus.PAID;
                this.paymentTransactionId = paid.transactionId();
                this.statusReason = null;
                if (this.totalAmount.signum() == 0) {
                    this.totalAmount = paid.amount();
                }
            }
            case OrderEvent.OrderCancelledEvent cancelled -> {
                this.status = OrderStatus.fromCancellation(cancelled.paymentDeclined());
                this.statusReason = cancelled.reason();
            }
        }
        this.lastEventAt = event.occurredAt();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public int getItemCount() {
        return itemCount;
    }

    public String getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }
}
