package br.com.bergamin.fulfillment.infrastructure.adapter.in.messaging;

import br.com.bergamin.fulfillment.support.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * O caminho completo do consumo: mensagem no topico -> projecao gravada -> notificacao
 * agendada, com idempotencia e DLQ.
 *
 * <p>Os payloads sao os que o servico de pedidos publica de verdade -- se o contrato entre
 * os dois quebrar, quebra aqui.</p>
 */
@EmbeddedKafka(partitions = 1, topics = {OrderEventsConsumerIT.TOPICO, OrderEventsConsumerIT.DLT})
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@DisplayName("Consumo de eventos (integracao)")
class OrderEventsConsumerIT extends AbstractIntegrationTest {

    static final String TOPICO = "orderflow.order-events";
    static final String DLT = "orderflow.order-events.DLT";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker broker;

    private Consumer<String, String> consumidorDlq;

    @BeforeEach
    void prepararCenario() {
        limparTudo();
    }

    @AfterEach
    void fecharConsumidor() {
        if (consumidorDlq != null) {
            consumidorDlq.close();
            consumidorDlq = null;
        }
    }

    @Test
    @DisplayName("Order.Placed monta a projecao e agenda a notificacao ao parceiro")
    void consomeOrderPlaced() {
        UUID pedido = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();

        publicar(UUID.randomUUID(), "Order.Placed", pedido, """
                {
                  "orderId": "%s",
                  "customerId": "%s",
                  "total": {"amount": 919.80},
                  "items": [{"productId": "%s", "sku": "TEC-001", "quantity": 2}],
                  "occurredAt": "2026-08-11T12:00:00Z"
                }
                """.formatted(pedido, cliente, UUID.randomUUID()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(statusDoPedido(pedido)).isEqualTo("PENDING");
            assertThat(contarNotificacoes()).isEqualTo(1);
        });

        assertThat(jdbcTemplate.queryForObject(
                "SELECT customer_id FROM order_snapshot WHERE order_id = ?", UUID.class, pedido))
                .isEqualTo(cliente);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT type FROM notification WHERE order_id = ?", String.class, pedido))
                .isEqualTo("ORDER_RECEIVED");
    }

    @Test
    @DisplayName("a mesma mensagem entregue duas vezes gera um efeito so")
    void consumoIdempotente() {
        UUID pedido = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String payload = """
                {
                  "orderId": "%s",
                  "customerId": "%s",
                  "total": {"amount": 100.00},
                  "items": [{"productId": "%s", "sku": "X", "quantity": 1}],
                  "occurredAt": "2026-08-11T12:00:00Z"
                }
                """.formatted(pedido, UUID.randomUUID(), UUID.randomUUID());

        // Mesmo eventId: e exatamente o que acontece numa reentrega apos rebalanceamento
        // de consumidores ou timeout de commit de offset.
        publicar(eventId, "Order.Placed", pedido, payload);
        publicar(eventId, "Order.Placed", pedido, payload);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(contarEventosProcessados()).isEqualTo(1));

        // A garantia que importa: o parceiro nao recebe a mesma notificacao duas vezes.
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(contarNotificacoes()).isEqualTo(1));
    }

    @Test
    @DisplayName("Order.Paid depois de Order.Placed leva o pedido a PAID")
    void aplicaSequenciaDeEventos() {
        UUID pedido = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();

        publicar(UUID.randomUUID(), "Order.Placed", pedido, """
                {"orderId": "%s", "customerId": "%s", "total": {"amount": 500.00},
                 "items": [{"productId": "%s", "sku": "X", "quantity": 1}],
                 "occurredAt": "2026-08-11T12:00:00Z"}
                """.formatted(pedido, cliente, UUID.randomUUID()));

        publicar(UUID.randomUUID(), "Order.Paid", pedido, """
                {"orderId": "%s", "transactionId": "tx_777", "amount": {"amount": 500.00},
                 "occurredAt": "2026-08-11T12:05:00Z"}
                """.formatted(pedido));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(statusDoPedido(pedido)).isEqualTo("PAID");
            assertThat(contarNotificacoes()).isEqualTo(2);
        });

        assertThat(jdbcTemplate.queryForObject(
                "SELECT payment_transaction_id FROM order_snapshot WHERE order_id = ?", String.class, pedido))
                .isEqualTo("tx_777");
    }

    @Test
    @DisplayName("mensagem envenenada vai direto para a DLQ sem travar a particao")
    void mensagemEnvenenadaVaiParaDlq() {
        consumidorDlq = criarConsumidorDlq();

        UUID pedidoQuebrado = UUID.randomUUID();
        publicar(UUID.randomUUID(), "Order.Placed", pedidoQuebrado, "{isso definitivamente nao e json");

        ConsumerRecord<String, String> naDlq =
                KafkaTestUtils.getSingleRecord(consumidorDlq, DLT, Duration.ofSeconds(30));
        assertThat(naDlq.value()).contains("nao e json");

        // A prova de que a fila nao travou: a mensagem seguinte e processada normalmente.
        UUID pedidoBom = UUID.randomUUID();
        publicar(UUID.randomUUID(), "Order.Placed", pedidoBom, """
                {"orderId": "%s", "customerId": "%s", "total": {"amount": 10.00},
                 "items": [{"productId": "%s", "sku": "X", "quantity": 1}],
                 "occurredAt": "2026-08-11T12:00:00Z"}
                """.formatted(pedidoBom, UUID.randomUUID(), UUID.randomUUID()));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(statusDoPedido(pedidoBom)).isEqualTo("PENDING"));

        assertThat(statusDoPedido(pedidoQuebrado)).isNull();
    }

    @Test
    @DisplayName("guarda o trace da requisicao que originou o evento no outro servico")
    void guardaTraceDeOrigem() {
        UUID pedido = UUID.randomUUID();
        String traceDaRequisicao = "4bf92f3577b34da6a3ce929d0e0e4736";

        publicar(UUID.randomUUID(), "Order.Placed", pedido, """
                {"orderId": "%s", "customerId": "%s", "total": {"amount": 75.00},
                 "items": [{"productId": "%s", "sku": "X", "quantity": 1}],
                 "occurredAt": "2026-08-11T12:00:00Z"}
                """.formatted(pedido, UUID.randomUUID(), UUID.randomUUID()), traceDaRequisicao);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(contarEventosProcessados()).isEqualTo(1));

        // E o que permite, partindo de um chamado do cliente, achar tudo que aconteceu nos
        // dois servicos por causa daquela unica requisicao.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT trace_id FROM processed_event", String.class))
                .isEqualTo(traceDaRequisicao);
    }

    @Test
    @DisplayName("mensagem sem trace continua sendo processada normalmente")
    void toleraMensagemSemTrace() {
        UUID pedido = UUID.randomUUID();

        publicar(UUID.randomUUID(), "Order.Placed", pedido, """
                {"orderId": "%s", "customerId": "%s", "total": {"amount": 10.00},
                 "items": [{"productId": "%s", "sku": "X", "quantity": 1}],
                 "occurredAt": "2026-08-11T12:00:00Z"}
                """.formatted(pedido, UUID.randomUUID(), UUID.randomUUID()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(statusDoPedido(pedido)).isEqualTo("PENDING"));

        // Observabilidade nao pode ser requisito para o negocio funcionar.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT trace_id FROM processed_event", String.class)).isNull();
    }

    // ---------------------------------------------------------------- auxiliares

    private void publicar(UUID eventId, String eventType, UUID orderId, String payload) {
        publicar(eventId, eventType, orderId, payload, null);
    }

    private void publicar(UUID eventId, String eventType, UUID orderId, String payload, String traceId) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPICO, orderId.toString(), payload);
        record.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        if (traceId != null) {
            record.headers().add("traceId", traceId.getBytes(StandardCharsets.UTF_8));
        }
        try {
            kafkaTemplate.send(record).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao publicar mensagem de teste", e);
        }
    }

    private String statusDoPedido(UUID orderId) {
        var linhas = jdbcTemplate.queryForList(
                "SELECT status FROM order_snapshot WHERE order_id = ?", String.class, orderId);
        return linhas.isEmpty() ? null : linhas.get(0);
    }

    private Consumer<String, String> criarConsumidorDlq() {
        var props = KafkaTestUtils.consumerProps("teste-dlq-" + UUID.randomUUID(), "true", broker);
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, DLT);
        return consumer;
    }
}
