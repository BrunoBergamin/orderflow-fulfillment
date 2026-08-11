package br.com.bergamin.fulfillment.application.service;

import br.com.bergamin.fulfillment.application.port.in.DispatchNotificationsUseCase;
import br.com.bergamin.fulfillment.application.port.out.NotificationRepositoryPort;
import br.com.bergamin.fulfillment.application.port.out.NotificationSenderPort;
import br.com.bergamin.fulfillment.domain.exception.NotificationDeliveryException;
import br.com.bergamin.fulfillment.domain.model.Notification;
import br.com.bergamin.fulfillment.domain.model.NotificationStatus;
import br.com.bergamin.fulfillment.domain.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatchService")
class NotificationDispatchServiceTest {

    private static final Instant AGORA = Instant.parse("2026-08-11T12:00:00Z");

    @Mock
    private NotificationRepositoryPort notifications;
    @Mock
    private NotificationSenderPort sender;
    @Mock
    private NotificationOutcomeWriter outcomeWriter;

    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        service = new NotificationDispatchService(notifications, sender, outcomeWriter,
                Clock.fixed(AGORA, ZoneOffset.UTC), 50);
    }

    private Notification pendente() {
        return Notification.schedule(UUID.randomUUID(), NotificationType.ORDER_RECEIVED, "mensagem", AGORA);
    }

    @Test
    @DisplayName("sem nada vencido, nao chama o parceiro")
    void loteVazio() {
        when(notifications.lockDueBatch(AGORA, 50)).thenReturn(List.of());

        DispatchNotificationsUseCase.Report report = service.dispatchDue();

        assertThat(report.attempted()).isZero();
        verifyNoInteractions(sender, outcomeWriter);
    }

    @Test
    @DisplayName("entrega bem-sucedida marca como enviada")
    void entregaComSucesso() {
        Notification notification = pendente();
        when(notifications.lockDueBatch(AGORA, 50)).thenReturn(List.of(notification));

        DispatchNotificationsUseCase.Report report = service.dispatchDue();

        assertThat(report.sent()).isEqualTo(1);
        verify(outcomeWriter).markSent(notification.getId());
        verify(outcomeWriter, never()).registerFailure(any(), anyString());
    }

    @Test
    @DisplayName("falha do parceiro reagenda em vez de perder a notificacao")
    void falhaReagenda() {
        Notification notification = pendente();
        when(notifications.lockDueBatch(AGORA, 50)).thenReturn(List.of(notification));
        doThrow(new NotificationDeliveryException("HTTP 503")).when(sender).deliver(notification);
        when(outcomeWriter.registerFailure(eq(notification.getId()), anyString()))
                .thenReturn(NotificationStatus.PENDING);

        DispatchNotificationsUseCase.Report report = service.dispatchDue();

        assertThat(report.retryScheduled()).isEqualTo(1);
        assertThat(report.dead()).isZero();
    }

    @Test
    @DisplayName("uma entrega que falha nao impede as outras do mesmo lote")
    void faltaDeUmaNaoDerrubaOLote() {
        Notification primeira = pendente();
        Notification problematica = pendente();
        Notification terceira = pendente();

        when(notifications.lockDueBatch(AGORA, 50))
                .thenReturn(List.of(primeira, problematica, terceira));
        // So a do meio falha; as outras passam pelo mesmo mock sem estouro.
        doAnswer(invocation -> {
            Notification alvo = invocation.getArgument(0);
            if (alvo.getId().equals(problematica.getId())) {
                throw new NotificationDeliveryException("HTTP 503");
            }
            return null;
        }).when(sender).deliver(any());
        when(outcomeWriter.registerFailure(eq(problematica.getId()), anyString()))
                .thenReturn(NotificationStatus.DEAD);

        DispatchNotificationsUseCase.Report report = service.dispatchDue();

        assertThat(report.attempted()).isEqualTo(3);
        assertThat(report.sent()).isEqualTo(2);
        assertThat(report.dead()).isEqualTo(1);
        verify(outcomeWriter).markSent(primeira.getId());
        verify(outcomeWriter).markSent(terceira.getId());
    }
}
