package br.com.bergamin.fulfillment.application;

import br.com.bergamin.fulfillment.application.port.in.DispatchNotificationsUseCase;
import br.com.bergamin.fulfillment.application.port.out.NotificationRepositoryPort;
import br.com.bergamin.fulfillment.domain.model.Notification;
import br.com.bergamin.fulfillment.domain.model.NotificationType;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.partner.PartnerSimulator;
import br.com.bergamin.fulfillment.support.AbstractIntegrationTest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comportamento do sistema quando o parceiro externo cai.
 *
 * <p>O que precisa valer: nenhuma notificacao se perde, o circuito abre para parar de bater
 * num servico que ja esta fora, e tudo volta a fluir quando o parceiro retorna.</p>
 */
@DisplayName("Circuit breaker no parceiro (integracao)")
class CircuitBreakerIT extends AbstractIntegrationTest {

    @Autowired
    private DispatchNotificationsUseCase dispatchNotifications;

    @Autowired
    private NotificationRepositoryPort notifications;

    @Autowired
    private PartnerSimulator partner;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void prepararCenario() {
        limparTudo();
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("partner");
        circuitBreaker.reset();
        partner.setAvailable(true);
    }

    @AfterEach
    void restaurarParceiro() {
        partner.setAvailable(true);
        circuitBreaker.reset();
    }

    @Test
    @DisplayName("com o parceiro no ar, as notificacoes sao entregues e o circuito fica fechado")
    void entregaComParceiroDisponivel() {
        agendarNotificacoes(3);

        DispatchNotificationsUseCase.Report report = dispatchNotifications.dispatchDue();

        assertThat(report.sent()).isEqualTo(3);
        assertThat(report.dead()).isZero();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(contarPorStatus("SENT")).isEqualTo(3);
    }

    @Test
    @DisplayName("com o parceiro fora, o circuito abre e nenhuma notificacao se perde")
    void abreOCircuitoEPreservaAsNotificacoes() {
        agendarNotificacoes(5);
        partner.setAvailable(false);

        DispatchNotificationsUseCase.Report report = dispatchNotifications.dispatchDue();

        assertThat(report.sent()).isZero();
        assertThat(report.attempted()).isEqualTo(5);

        assertThat(circuitBreaker.getState())
                .as("depois de falhas suficientes, para de tentar em vez de insistir")
                .isEqualTo(CircuitBreaker.State.OPEN);

        // O ponto principal: nada foi descartado. Tudo continua pendente, com o erro
        // registrado e uma nova tentativa agendada.
        assertThat(contarPorStatus("PENDING")).isEqualTo(5);
        assertThat(contarPorStatus("SENT")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE attempts > 0 AND last_error IS NOT NULL", Long.class))
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("quando o parceiro volta, as notificacoes represadas sao entregues")
    void entregaOAcumuladoQuandoOParceiroVolta() {
        agendarNotificacoes(3);
        partner.setAvailable(false);
        dispatchNotifications.dispatchDue();
        assertThat(contarPorStatus("PENDING")).isEqualTo(3);

        // Parceiro volta. O circuito e reiniciado explicitamente porque este teste verifica
        // a retomada da entrega, nao o tempo de espera do circuito.
        partner.setAvailable(true);
        circuitBreaker.reset();
        liberarParaTentarAgora();

        DispatchNotificationsUseCase.Report report = dispatchNotifications.dispatchDue();

        assertThat(report.sent()).isEqualTo(3);
        assertThat(contarPorStatus("SENT")).isEqualTo(3);
    }

    private void agendarNotificacoes(int quantidade) {
        for (int i = 0; i < quantidade; i++) {
            notifications.save(Notification.schedule(
                    UUID.randomUUID(), NotificationType.ORDER_RECEIVED, "mensagem " + i, Instant.now()));
        }
    }

    /** Antecipa o agendamento das tentativas para nao esperar o backoff no teste. */
    private void liberarParaTentarAgora() {
        jdbcTemplate.update("UPDATE notification SET next_attempt_at = ? WHERE status = 'PENDING'",
                OffsetDateTime.ofInstant(Instant.now().minusSeconds(1), ZoneOffset.UTC));
    }

    private long contarPorStatus(String status) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE status = ?", Long.class, status);
        return total == null ? 0 : total;
    }
}
