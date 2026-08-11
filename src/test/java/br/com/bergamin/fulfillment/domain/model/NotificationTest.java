package br.com.bergamin.fulfillment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Notification")
class NotificationTest {

    private static final Instant AGORA = Instant.parse("2026-08-11T12:00:00Z");

    private Notification pendente() {
        return Notification.schedule(UUID.randomUUID(), NotificationType.ORDER_RECEIVED, "mensagem", AGORA);
    }

    @Test
    @DisplayName("nasce pendente e ja vencida, para ser entregue no proximo ciclo")
    void nascePendente() {
        Notification notification = pendente();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isZero();
        assertThat(notification.isDue(AGORA)).isTrue();
    }

    @Test
    @DisplayName("entrega bem-sucedida limpa erro e agendamento")
    void marcaEnviada() {
        Notification notification = pendente();

        notification.markSent(AGORA);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(AGORA);
        assertThat(notification.getNextAttemptAt()).isNull();
        assertThat(notification.isDue(AGORA.plusSeconds(3600))).isFalse();
    }

    @Test
    @DisplayName("falha reagenda com espera crescente e nao entrega antes da hora")
    void reagendaComBackoff() {
        Notification notification = pendente();

        notification.registerFailure("parceiro fora do ar", AGORA);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getNextAttemptAt()).isEqualTo(AGORA.plusSeconds(30));
        assertThat(notification.isDue(AGORA.plusSeconds(29))).isFalse();
        assertThat(notification.isDue(AGORA.plusSeconds(30))).isTrue();
    }

    @Test
    @DisplayName("o intervalo dobra a cada tentativa, com teto de 30 minutos")
    void backoffExponencialComTeto() {
        assertThat(Notification.backoffFor(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(Notification.backoffFor(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(Notification.backoffFor(3)).isEqualTo(Duration.ofSeconds(120));

        // Sem teto, a espera cresceria indefinidamente e a notificacao nunca mais sairia.
        assertThat(Notification.backoffFor(20)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("apos o limite de tentativas fica DEAD e para de ser tentada")
    void morreAposOLimite() {
        Notification notification = pendente();

        for (int tentativa = 0; tentativa < Notification.MAX_ATTEMPTS; tentativa++) {
            notification.registerFailure("erro " + tentativa, AGORA);
        }

        assertThat(notification.isDead()).isTrue();
        assertThat(notification.getAttempts()).isEqualTo(Notification.MAX_ATTEMPTS);
        assertThat(notification.getNextAttemptAt()).isNull();
        assertThat(notification.isDue(AGORA.plusSeconds(86_400))).isFalse();
        // Continua registrada, com o ultimo erro, para inspecao humana.
        assertThat(notification.getLastError()).isEqualTo("erro 4");
    }

    @Test
    @DisplayName("mensagem de erro gigante e truncada para caber na coluna")
    void truncaErroLongo() {
        Notification notification = pendente();

        notification.registerFailure("x".repeat(900), AGORA);

        assertThat(notification.getLastError()).hasSize(500);
    }
}
