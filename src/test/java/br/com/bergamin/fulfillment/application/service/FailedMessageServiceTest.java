package br.com.bergamin.fulfillment.application.service;

import br.com.bergamin.fulfillment.application.port.out.FailedMessageRepositoryPort;
import br.com.bergamin.fulfillment.application.port.out.MessageRepublisherPort;
import br.com.bergamin.fulfillment.domain.exception.InvalidMessageStateException;
import br.com.bergamin.fulfillment.domain.exception.ResourceNotFoundException;
import br.com.bergamin.fulfillment.domain.model.FailedMessage;
import br.com.bergamin.fulfillment.domain.model.FailedMessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FailedMessageService")
class FailedMessageServiceTest {

    private static final Instant AGORA = Instant.parse("2026-08-11T12:00:00Z");

    @Mock
    private FailedMessageRepositoryPort messages;
    @Mock
    private MessageRepublisherPort republisher;

    private FailedMessageService service;

    @BeforeEach
    void setUp() {
        service = new FailedMessageService(messages, republisher, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private FailedMessage pendente() {
        return FailedMessage.received(UUID.randomUUID(), "Order.Placed", "agg-1",
                "{}", "erro", "orderflow.order-events", 0, 1L, null, AGORA);
    }

    @Test
    @DisplayName("reenvia ao topico e so entao marca como resolvida")
    void reenviaEMarca() {
        FailedMessage mensagem = pendente();
        when(messages.findById(mensagem.getId())).thenReturn(Optional.of(mensagem));
        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));

        FailedMessage resultado = service.reprocess(mensagem.getId());

        assertThat(resultado.getStatus()).isEqualTo(FailedMessageStatus.REPROCESSED);
        verify(republisher).republish(mensagem);
        verify(messages).save(mensagem);
    }

    @Test
    @DisplayName("se o broker recusar, a mensagem continua pendente")
    void falhaNoEnvioMantemPendente() {
        FailedMessage mensagem = pendente();
        when(messages.findById(mensagem.getId())).thenReturn(Optional.of(mensagem));
        doThrow(new IllegalStateException("broker fora")).when(republisher).republish(mensagem);

        assertThatThrownBy(() -> service.reprocess(mensagem.getId()))
                .isInstanceOf(IllegalStateException.class);

        // O ponto do teste: marcar antes de enviar faria a mensagem sumir da lista de
        // pendencias sem nunca ter saido.
        assertThat(mensagem.getStatus()).isEqualTo(FailedMessageStatus.PENDING);
        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("descarte registra o motivo e nao toca no broker")
    void descarta() {
        FailedMessage mensagem = pendente();
        when(messages.findById(mensagem.getId())).thenReturn(Optional.of(mensagem));
        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));

        FailedMessage resultado = service.discard(mensagem.getId(), "duplicada de um teste");

        assertThat(resultado.getStatus()).isEqualTo(FailedMessageStatus.DISCARDED);
        assertThat(resultado.getResolutionNote()).isEqualTo("duplicada de um teste");
        verifyNoInteractions(republisher);
    }

    @Test
    @DisplayName("mensagem sem eventId e recusada antes de chegar ao broker")
    void recusaAntesDePublicar() {
        FailedMessage semId = FailedMessage.received(null, null, "agg-1",
                "{}", "cabecalho ausente", "orderflow.order-events", 0, 1L, null, AGORA);
        when(messages.findById(semId.getId())).thenReturn(Optional.of(semId));

        assertThatThrownBy(() -> service.reprocess(semId.getId()))
                .isInstanceOf(InvalidMessageStateException.class);

        // Publicar para falhar do outro lado seria so mudar o lugar do erro.
        verifyNoInteractions(republisher);
        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("mensagem inexistente vira 404 de dominio")
    void inexistente() {
        UUID id = UUID.randomUUID();
        when(messages.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reprocess(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
