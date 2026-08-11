package br.com.bergamin.fulfillment.infrastructure.adapter.in.messaging;

import br.com.bergamin.fulfillment.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O ciclo completo da fila de mensagens mortas: cair, aparecer e ter um caminho de volta.
 *
 * <p>Quase todo projeto configura DLQ; poucos dao a ela um caminho de volta. Estes testes
 * cobrem justamente essa parte -- a mensagem que falhou vira uma linha consultavel, e o
 * reenvio realmente reprocessa o evento.</p>
 */
@EmbeddedKafka(partitions = 1, topics = {DeadLetterQueueIT.TOPICO, DeadLetterQueueIT.DLT})
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@AutoConfigureMockMvc
@DisplayName("Fila de mensagens mortas (integracao)")
class DeadLetterQueueIT extends AbstractIntegrationTest {

    static final String TOPICO = "orderflow.order-events";
    static final String DLT = "orderflow.order-events.DLT";
    private static final String API_KEY = "chave-de-teste";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void prepararCenario() {
        limparTudo();
    }

    @Test
    @DisplayName("mensagem envenenada vira uma linha consultavel pela API")
    void mensagemEnvenenadaViraLinha() throws Exception {
        UUID eventId = UUID.randomUUID();
        publicar(eventId, "Order.Placed", UUID.randomUUID(), "{json quebrado");

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(contarMensagensMortas()).isEqualTo(1));

        mockMvc.perform(get("/api/v1/failed-messages").param("status", "PENDING")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].errorMessage").isNotEmpty())
                .andExpect(jsonPath("$.content[0].originalTopic").value(TOPICO));

        mockMvc.perform(get("/api/v1/failed-messages/summary").header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(1));
    }

    @Test
    @DisplayName("reenviar devolve a mensagem ao topico e o evento e finalmente aplicado")
    void reenvioAplicaOEvento() throws Exception {
        UUID pedido = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        // Simula uma mensagem que falhou por causa transitoria: o conteudo e valido, entao
        // reenviar resolve. O caso oposto (payload corrompido) so tem o caminho do descarte.
        UUID mensagemId = inserirMensagemMorta(eventId, pedido, """
                {"orderId": "%s", "customerId": "%s", "total": {"amount": 250.00},
                 "items": [{"productId": "%s", "sku": "X", "quantity": 1}],
                 "occurredAt": "2026-08-11T12:00:00Z"}
                """.formatted(pedido, UUID.randomUUID(), UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/failed-messages/" + mensagemId + "/reprocess")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPROCESSED"))
                .andExpect(jsonPath("$.reprocessCount").value(1));

        // A prova de que o reenvio serviu para alguma coisa: o pedido aparece na projecao.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(statusDoPedido(pedido)).isEqualTo("PENDING"));
        assertThat(contarNotificacoes()).isEqualTo(1);
    }

    @Test
    @DisplayName("reenviar duas vezes devolve conflito")
    void naoReenviaDuasVezes() throws Exception {
        UUID mensagemId = inserirMensagemMorta(UUID.randomUUID(), UUID.randomUUID(), "{}");

        mockMvc.perform(post("/api/v1/failed-messages/" + mensagemId + "/reprocess")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/failed-messages/" + mensagemId + "/reprocess")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("REPROCESSED"));
    }

    @Test
    @DisplayName("mensagem sem eventId so pode ser descartada, com motivo")
    void semEventIdSoDescarta() throws Exception {
        UUID mensagemId = inserirMensagemMorta(null, UUID.randomUUID(), "{}");

        mockMvc.perform(get("/api/v1/failed-messages/" + mensagemId).header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReprocess").value(false));

        mockMvc.perform(post("/api/v1/failed-messages/" + mensagemId + "/reprocess")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/failed-messages/" + mensagemId + "/discard")
                        .header("X-API-Key", API_KEY)
                        .contentType("application/json")
                        .content("""
                                {"reason": "produtor corrigido, evento reenviado na origem"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"))
                .andExpect(jsonPath("$.resolutionNote").value("produtor corrigido, evento reenviado na origem"));
    }

    @Test
    @DisplayName("a rota da DLQ tambem exige chave de API")
    void exigeChaveDeApi() throws Exception {
        mockMvc.perform(get("/api/v1/failed-messages")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- auxiliares

    private void publicar(UUID eventId, String eventType, UUID orderId, String payload) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPICO, orderId.toString(), payload);
        record.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        try {
            kafkaTemplate.send(record).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao publicar mensagem de teste", e);
        }
    }

    private UUID inserirMensagemMorta(UUID eventId, UUID orderId, String payload) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO failed_message
                            (id, event_id, event_type, aggregate_id, payload, error_message,
                             original_topic, original_partition, original_offset, received_at,
                             status, reprocess_count)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 0, 1, ?, 'PENDING', 0)
                        """,
                id, eventId, eventId == null ? null : "Order.Placed", orderId.toString(), payload,
                "falha transitoria no banco", TOPICO, Timestamp.from(Instant.now()));
        return id;
    }

    private long contarMensagensMortas() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM failed_message", Long.class);
        return total == null ? 0 : total;
    }

    private String statusDoPedido(UUID orderId) {
        var linhas = jdbcTemplate.queryForList(
                "SELECT status FROM order_snapshot WHERE order_id = ?", String.class, orderId);
        return linhas.isEmpty() ? null : linhas.get(0);
    }
}
