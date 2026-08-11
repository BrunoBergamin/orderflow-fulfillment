package br.com.bergamin.fulfillment.domain.model;

import br.com.bergamin.fulfillment.domain.event.OrderEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderSnapshot")
class OrderSnapshotTest {

    private static final Instant T1 = Instant.parse("2026-08-11T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-08-11T12:05:00Z");
    private static final UUID PEDIDO = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();

    private OrderEvent.OrderPlacedEvent placed(Instant quando) {
        return new OrderEvent.OrderPlacedEvent(PEDIDO, CLIENTE, new BigDecimal("919.80"), 2, quando);
    }

    @Test
    @DisplayName("Order.Placed monta a projecao inicial como PENDING")
    void aplicaPlaced() {
        OrderSnapshot snapshot = OrderSnapshot.empty(PEDIDO, T1);

        snapshot.apply(placed(T1));

        assertThat(snapshot.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(snapshot.getCustomerId()).isEqualTo(CLIENTE);
        assertThat(snapshot.getTotalAmount()).isEqualByComparingTo("919.80");
        assertThat(snapshot.getItemCount()).isEqualTo(2);
        assertThat(snapshot.getLastEventAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("Order.Paid marca pago e guarda a transacao")
    void aplicaPaid() {
        OrderSnapshot snapshot = OrderSnapshot.empty(PEDIDO, T1);
        snapshot.apply(placed(T1));

        snapshot.apply(new OrderEvent.OrderPaidEvent(PEDIDO, "tx_9", new BigDecimal("919.80"), T2));

        assertThat(snapshot.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(snapshot.getPaymentTransactionId()).isEqualTo("tx_9");
    }

    @Test
    @DisplayName("cancelamento por recusa de pagamento vira PAYMENT_FAILED")
    void distingueRecusaDeDesistencia() {
        OrderSnapshot recusado = OrderSnapshot.empty(PEDIDO, T1);
        recusado.apply(new OrderEvent.OrderCancelledEvent(PEDIDO, "saldo insuficiente", true, T2));

        OrderSnapshot desistencia = OrderSnapshot.empty(PEDIDO, T1);
        desistencia.apply(new OrderEvent.OrderCancelledEvent(PEDIDO, "mudei de ideia", false, T2));

        assertThat(recusado.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(desistencia.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("evento atrasado e identificado como obsoleto")
    void identificaEventoAtrasado() {
        OrderSnapshot snapshot = OrderSnapshot.empty(PEDIDO, T1);
        snapshot.apply(new OrderEvent.OrderPaidEvent(PEDIDO, "tx_9", new BigDecimal("919.80"), T2));

        // Um Placed reprocessado da DLQ chega depois do Paid que veio atras dele.
        assertThat(snapshot.isStale(T1)).isTrue();
        assertThat(snapshot.isStale(T2)).isFalse();
    }

    @Test
    @DisplayName("Placed que chega depois do Paid nao rebaixa o pedido para PENDING")
    void naoRebaixaStatus() {
        OrderSnapshot snapshot = OrderSnapshot.empty(PEDIDO, T1);
        snapshot.apply(new OrderEvent.OrderPaidEvent(PEDIDO, "tx_9", new BigDecimal("919.80"), T1));

        snapshot.apply(placed(T2));

        // Preenche os dados que faltavam, mas o pedido continua pago.
        assertThat(snapshot.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(snapshot.getCustomerId()).isEqualTo(CLIENTE);
        assertThat(snapshot.getItemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("projecao vazia aceita Paid antes de Placed sem quebrar")
    void aceitaPaidSemPlaced() {
        OrderSnapshot snapshot = OrderSnapshot.empty(PEDIDO, T1);

        snapshot.apply(new OrderEvent.OrderPaidEvent(PEDIDO, "tx_9", new BigDecimal("500.00"), T1));

        assertThat(snapshot.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(snapshot.getTotalAmount()).isEqualByComparingTo("500.00");
    }
}
