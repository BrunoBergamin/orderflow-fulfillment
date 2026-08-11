package br.com.bergamin.fulfillment.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Os fatos que este servico sabe reagir, publicados pelo servico de pedidos.
 *
 * <p>Interface {@code sealed}: a lista de eventos e fechada e conhecida em tempo de
 * compilacao. Quem trata esses eventos usa {@code switch} sem {@code default} -- se um
 * evento novo entrar aqui, o compilador aponta todos os pontos que precisam decidir o que
 * fazer com ele, em vez de o evento cair silenciosamente num ramo generico.</p>
 *
 * <p>Sem clausula {@code permits}: como as implementacoes sao registros aninhados no proprio
 * arquivo, o compilador as infere.</p>
 *
 * <p>Estes tipos sao <b>deste</b> servico, nao importados do produtor. Dois servicos
 * compartilhando um jar de modelo voltam a ser um monolito com passos extras: qualquer
 * mudanca no produtor obrigaria a recompilar e reimplantar o consumidor. O acoplamento
 * aceito aqui e apenas o contrato do JSON.</p>
 */
public sealed interface OrderEvent {

    UUID orderId();

    /** Momento em que o fato ocorreu na origem -- usado para descartar evento atrasado. */
    Instant occurredAt();

    /** Nome do evento no contrato, ex.: {@code Order.Placed}. */
    String type();

    record OrderPlacedEvent(UUID orderId, UUID customerId, BigDecimal total,
                            int itemCount, Instant occurredAt) implements OrderEvent {
        public static final String TYPE = "Order.Placed";

        @Override
        public String type() {
            return TYPE;
        }
    }

    record OrderPaidEvent(UUID orderId, String transactionId, BigDecimal amount,
                          Instant occurredAt) implements OrderEvent {
        public static final String TYPE = "Order.Paid";

        @Override
        public String type() {
            return TYPE;
        }
    }

    record OrderCancelledEvent(UUID orderId, String reason, boolean paymentDeclined,
                               Instant occurredAt) implements OrderEvent {
        public static final String TYPE = "Order.Cancelled";

        @Override
        public String type() {
            return TYPE;
        }
    }
}
