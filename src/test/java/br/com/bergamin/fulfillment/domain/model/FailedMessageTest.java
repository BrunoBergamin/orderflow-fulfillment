package br.com.bergamin.fulfillment.domain.model;

import br.com.bergamin.fulfillment.domain.exception.InvalidMessageStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FailedMessage")
class FailedMessageTest {

    private static final Instant AGORA = Instant.parse("2026-08-11T12:00:00Z");

    private FailedMessage mensagem(UUID eventId, String eventType) {
        return FailedMessage.received(eventId, eventType, UUID.randomUUID().toString(),
                "{\"orderId\":\"x\"}", "erro qualquer", "orderflow.order-events",
                0, 42L, "trace-1", AGORA);
    }

    private FailedMessage mensagemCompleta() {
        return mensagem(UUID.randomUUID(), "Order.Placed");
    }

    @Test
    @DisplayName("nasce pendente e pode ser reenviada")
    void nascePendente() {
        FailedMessage mensagem = mensagemCompleta();

        assertThat(mensagem.getStatus()).isEqualTo(FailedMessageStatus.PENDING);
        assertThat(mensagem.isPending()).isTrue();
        assertThat(mensagem.canReprocess()).isTrue();
        assertThat(mensagem.getReprocessCount()).isZero();
    }

    @Test
    @DisplayName("mensagem sem eventId nao pode ser reenviada, so descartada")
    void semEventIdNaoReenvia() {
        FailedMessage semId = mensagem(null, "Order.Placed");

        // Cabecalho ausente e uma das causas de a mensagem ter falhado. Reenviar sem ele
        // produziria exatamente o mesmo erro.
        assertThat(semId.canReprocess()).isFalse();
        assertThatThrownBy(() -> semId.markReprocessed(AGORA))
                .isInstanceOf(InvalidMessageStateException.class)
                .hasMessageContaining("sem eventId");

        semId.discard("payload sem identificacao, corrigido na origem", AGORA);
        assertThat(semId.getStatus()).isEqualTo(FailedMessageStatus.DISCARDED);
    }

    @Test
    @DisplayName("mensagem sem eventType tambem nao pode ser reenviada")
    void semEventTypeNaoReenvia() {
        assertThat(mensagem(UUID.randomUUID(), null).canReprocess()).isFalse();
    }

    @Test
    @DisplayName("reenvio marca a mensagem como resolvida e conta a tentativa")
    void marcaReenviada() {
        FailedMessage mensagem = mensagemCompleta();

        mensagem.markReprocessed(AGORA);

        assertThat(mensagem.getStatus()).isEqualTo(FailedMessageStatus.REPROCESSED);
        assertThat(mensagem.getReprocessCount()).isEqualTo(1);
        assertThat(mensagem.getResolvedAt()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("nao reenvia duas vezes a mesma mensagem")
    void naoReenviaDuasVezes() {
        FailedMessage mensagem = mensagemCompleta();
        mensagem.markReprocessed(AGORA);

        // Se falhar de novo, ela volta para a DLQ como uma linha nova; esta ja cumpriu seu papel.
        assertThatThrownBy(() -> mensagem.markReprocessed(AGORA))
                .isInstanceOf(InvalidMessageStateException.class);
    }

    @Test
    @DisplayName("nao descarta o que ja foi reenviado")
    void naoDescartaReenviada() {
        FailedMessage mensagem = mensagemCompleta();
        mensagem.markReprocessed(AGORA);

        assertThatThrownBy(() -> mensagem.discard("mudei de ideia", AGORA))
                .isInstanceOf(InvalidMessageStateException.class);
    }

    @Test
    @DisplayName("descarte sem motivo registra que nao houve motivo")
    void descarteSemMotivo() {
        FailedMessage mensagem = mensagemCompleta();

        mensagem.discard(null, AGORA);

        // Nunca fica em branco: um descarte sem explicacao vira duvida no mes seguinte.
        assertThat(mensagem.getResolutionNote()).isEqualTo("descartada sem motivo informado");
    }

    @Test
    @DisplayName("guarda a origem da mensagem no Kafka para conferencia")
    void guardaOrigem() {
        FailedMessage mensagem = mensagemCompleta();

        assertThat(mensagem.getOriginalTopic()).isEqualTo("orderflow.order-events");
        assertThat(mensagem.getOriginalPartition()).isZero();
        assertThat(mensagem.getOriginalOffset()).isEqualTo(42L);
        assertThat(mensagem.getTraceId()).isEqualTo("trace-1");
    }
}
