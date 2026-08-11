package br.com.bergamin.fulfillment.infrastructure.adapter.in.messaging;

import br.com.bergamin.fulfillment.domain.event.OrderEvent;
import br.com.bergamin.fulfillment.domain.exception.UnparseableEventException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o contrato JSON contra payloads reais do servico de pedidos.
 *
 * <p>E o ponto mais fragil de uma integracao entre servicos: um campo renomeado do outro
 * lado quebra aqui. Fixar os payloads em teste faz a quebra aparecer no build, e nao em
 * producao.</p>
 */
@DisplayName("OrderEventJsonParser")
class OrderEventJsonParserTest {

    private final OrderEventJsonParser parser =
            new OrderEventJsonParser(new ObjectMapper().registerModule(new JavaTimeModule()));

    private static final String ORDER_ID = "0b0f2a8e-7f0a-4b3f-9a2c-2f7d1e5c9a11";
    private static final String CUSTOMER_ID = "5c4b3a2d-1e0f-4a9b-8c7d-6e5f4a3b2c1d";

    @Test
    @DisplayName("le Order.Placed, inclusive o total aninhado em {amount}")
    void leOrderPlaced() {
        String payload = """
                {
                  "orderId": "%s",
                  "customerId": "%s",
                  "total": {"amount": 919.80},
                  "items": [
                    {"productId": "%s", "sku": "TEC-001", "quantity": 2},
                    {"productId": "%s", "sku": "MOU-002", "quantity": 1}
                  ],
                  "occurredAt": "2026-08-11T12:00:00Z"
                }
                """.formatted(ORDER_ID, CUSTOMER_ID, UUID.randomUUID(), UUID.randomUUID());

        OrderEvent event = parser.parse(OrderEvent.OrderPlacedEvent.TYPE, payload);

        assertThat(event).isInstanceOfSatisfying(OrderEvent.OrderPlacedEvent.class, placed -> {
            assertThat(placed.orderId()).hasToString(ORDER_ID);
            assertThat(placed.customerId()).hasToString(CUSTOMER_ID);
            assertThat(placed.total()).isEqualByComparingTo("919.80");
            assertThat(placed.itemCount()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("le Order.Paid")
    void leOrderPaid() {
        String payload = """
                {
                  "orderId": "%s",
                  "transactionId": "tx_abc",
                  "amount": {"amount": 459.90},
                  "occurredAt": "2026-08-11T12:10:00Z"
                }
                """.formatted(ORDER_ID);

        OrderEvent event = parser.parse(OrderEvent.OrderPaidEvent.TYPE, payload);

        assertThat(event).isInstanceOfSatisfying(OrderEvent.OrderPaidEvent.class, paid -> {
            assertThat(paid.transactionId()).isEqualTo("tx_abc");
            assertThat(paid.amount()).isEqualByComparingTo("459.90");
        });
    }

    @Test
    @DisplayName("le Order.Cancelled com a marca de recusa de pagamento")
    void leOrderCancelled() {
        String payload = """
                {
                  "orderId": "%s",
                  "reason": "saldo insuficiente",
                  "paymentDeclined": true,
                  "occurredAt": "2026-08-11T12:10:00Z"
                }
                """.formatted(ORDER_ID);

        OrderEvent event = parser.parse(OrderEvent.OrderCancelledEvent.TYPE, payload);

        assertThat(event).isInstanceOfSatisfying(OrderEvent.OrderCancelledEvent.class, cancelled -> {
            assertThat(cancelled.reason()).isEqualTo("saldo insuficiente");
            assertThat(cancelled.paymentDeclined()).isTrue();
        });
    }

    @Test
    @DisplayName("ignora campos novos do produtor em vez de quebrar")
    void toleraCamposDesconhecidos() {
        String payload = """
                {
                  "orderId": "%s",
                  "transactionId": "tx_abc",
                  "amount": {"amount": 10.00},
                  "occurredAt": "2026-08-11T12:10:00Z",
                  "campoNovoQueOProdutorAdicionou": {"qualquer": "coisa"}
                }
                """.formatted(ORDER_ID);

        assertThat(parser.parse(OrderEvent.OrderPaidEvent.TYPE, payload)).isNotNull();
    }

    @Test
    @DisplayName("tipo de evento desconhecido e mensagem envenenada")
    void recusaTipoDesconhecido() {
        assertThatThrownBy(() -> parser.parse("Order.Teleported", "{}"))
                .isInstanceOf(UnparseableEventException.class)
                .hasMessageContaining("desconhecido");
    }

    @Test
    @DisplayName("JSON quebrado e mensagem envenenada")
    void recusaJsonInvalido() {
        assertThatThrownBy(() -> parser.parse(OrderEvent.OrderPaidEvent.TYPE, "{isso nao e json"))
                .isInstanceOf(UnparseableEventException.class);
    }

    @Test
    @DisplayName("campo obrigatorio ausente e mensagem envenenada")
    void recusaCampoObrigatorioAusente() {
        assertThatThrownBy(() -> parser.parse(OrderEvent.OrderPaidEvent.TYPE,
                """
                        {"transactionId": "tx_abc", "occurredAt": "2026-08-11T12:10:00Z"}
                        """))
                .isInstanceOf(UnparseableEventException.class)
                .hasMessageContaining("orderId");
    }
}
