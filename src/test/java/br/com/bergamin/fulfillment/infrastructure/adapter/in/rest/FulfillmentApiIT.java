package br.com.bergamin.fulfillment.infrastructure.adapter.in.rest;

import br.com.bergamin.fulfillment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("API de leitura (integracao)")
class FulfillmentApiIT extends AbstractIntegrationTest {

    private static final String API_KEY = "chave-de-teste";

    @Autowired
    private MockMvc mockMvc;

    private UUID cliente;
    private UUID pedidoPago;

    @BeforeEach
    void prepararCenario() {
        limparTudo();
        cliente = UUID.randomUUID();
        pedidoPago = inserirProjecao(cliente, "PAID", "919.80");
        inserirProjecao(cliente, "PENDING", "150.00");
        inserirProjecao(UUID.randomUUID(), "PAID", "80.00");
    }

    @Test
    @DisplayName("sem X-API-Key devolve 401 em problem+json")
    void exigeChaveDeApi() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + pedidoPago))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Nao autenticado"));
    }

    @Test
    @DisplayName("chave errada tambem devolve 401")
    void recusaChaveInvalida() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + pedidoPago).header("X-API-Key", "chave-errada"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("consulta um pedido da projecao")
    void consultaPedido() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + pedidoPago).header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(pedidoPago.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalAmount").value(919.80));
    }

    @Test
    @DisplayName("pedido desconhecido devolve 404")
    void pedidoInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + UUID.randomUUID()).header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("filtra por cliente e por status")
    void filtra() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("customerId", cliente.toString())
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/orders").param("status", "PAID").header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/orders")
                        .param("customerId", cliente.toString()).param("status", "PENDING")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("status invalido na query devolve 400, nao 500")
    void statusInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("status", "INEXISTENTE").header("X-API-Key", API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parametro invalido"));
    }

    /**
     * Prova que a segunda leitura vem do Redis, e nao do banco.
     *
     * <p>Depois da primeira consulta (que popula o cache), a linha e apagada do PostgreSQL.
     * Se a resposta seguinte ainda vier, so pode ter vindo do cache.</p>
     */
    @Test
    @DisplayName("a segunda consulta e servida pelo cache")
    void segundaConsultaVemDoCache() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + pedidoPago).header("X-API-Key", API_KEY))
                .andExpect(status().isOk());

        assertThat(redisTemplate.hasKey("fulfillment:order:" + pedidoPago)).isTrue();

        jdbcTemplate.update("DELETE FROM order_snapshot WHERE order_id = ?", pedidoPago);

        mockMvc.perform(get("/api/v1/orders/" + pedidoPago).header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("documentacao e health ficam fora da protecao por chave")
    void rotasPublicas() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    private UUID inserirProjecao(UUID customerId, String status, String total) {
        UUID orderId = UUID.randomUUID();
        // O driver do PostgreSQL nao infere o tipo SQL de um Instant; OffsetDateTime mapeia
        // direto para TIMESTAMPTZ.
        OffsetDateTime agora = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO order_snapshot
                    (order_id, customer_id, status, total_amount, item_count, first_seen_at, last_event_at)
                VALUES (?, ?, ?, ?, 1, ?, ?)
                """, orderId, customerId, status, new BigDecimal(total), agora, agora);
        return orderId;
    }
}
