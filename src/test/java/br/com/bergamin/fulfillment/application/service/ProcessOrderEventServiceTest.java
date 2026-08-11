package br.com.bergamin.fulfillment.application.service;

import br.com.bergamin.fulfillment.application.port.in.ProcessOrderEventUseCase;
import br.com.bergamin.fulfillment.application.port.out.NotificationRepositoryPort;
import br.com.bergamin.fulfillment.application.port.out.OrderSnapshotCachePort;
import br.com.bergamin.fulfillment.application.port.out.OrderSnapshotRepositoryPort;
import br.com.bergamin.fulfillment.application.port.out.ProcessedEventPort;
import br.com.bergamin.fulfillment.domain.event.OrderEvent;
import br.com.bergamin.fulfillment.domain.model.Notification;
import br.com.bergamin.fulfillment.domain.model.NotificationType;
import br.com.bergamin.fulfillment.domain.model.OrderSnapshot;
import br.com.bergamin.fulfillment.domain.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessOrderEventService")
class ProcessOrderEventServiceTest {

    private static final Instant AGORA = Instant.parse("2026-08-11T12:00:00Z");
    private static final Instant ANTES = Instant.parse("2026-08-11T11:00:00Z");
    private static final UUID EVENTO = UUID.randomUUID();
    private static final UUID PEDIDO = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();

    @Mock
    private ProcessedEventPort processedEvents;
    @Mock
    private OrderSnapshotRepositoryPort snapshots;
    @Mock
    private NotificationRepositoryPort notifications;
    @Mock
    private OrderSnapshotCachePort cache;

    private ProcessOrderEventService service;

    @BeforeEach
    void setUp() {
        service = new ProcessOrderEventService(processedEvents, snapshots, notifications, cache,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private OrderEvent.OrderPlacedEvent placed(Instant quando) {
        return new OrderEvent.OrderPlacedEvent(PEDIDO, CLIENTE, new BigDecimal("919.80"), 2, quando);
    }

    @Test
    @DisplayName("aplica o evento, invalida o cache e agenda a notificacao")
    void processaEventoNovo() {
        when(processedEvents.markProcessed(eq(EVENTO), anyString(), eq(AGORA))).thenReturn(true);
        when(snapshots.findById(PEDIDO)).thenReturn(Optional.empty());
        when(snapshots.save(any(OrderSnapshot.class))).thenAnswer(i -> i.getArgument(0));

        var outcome = service.process(new ProcessOrderEventUseCase.Command(EVENTO, placed(AGORA)));

        assertThat(outcome).isEqualTo(ProcessOrderEventUseCase.Outcome.PROCESSED);

        ArgumentCaptor<OrderSnapshot> snapshotCaptor = ArgumentCaptor.forClass(OrderSnapshot.class);
        verify(snapshots).save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.ORDER_RECEIVED);

        verify(cache).evictAfterCommit(PEDIDO);
    }

    @Test
    @DisplayName("reentrega do mesmo evento nao gera segunda notificacao")
    void ignoraDuplicata() {
        when(processedEvents.markProcessed(eq(EVENTO), anyString(), eq(AGORA))).thenReturn(false);

        var outcome = service.process(new ProcessOrderEventUseCase.Command(EVENTO, placed(AGORA)));

        assertThat(outcome).isEqualTo(ProcessOrderEventUseCase.Outcome.DUPLICATE_IGNORED);

        // O ponto do teste: nada acontece na segunda passagem.
        verifyNoInteractions(snapshots, notifications, cache);
    }

    @Test
    @DisplayName("evento atrasado nao regride a projecao")
    void ignoraEventoAtrasado() {
        OrderSnapshot jaPago = OrderSnapshot.restore(PEDIDO, CLIENTE, OrderStatus.PAID,
                new BigDecimal("919.80"), 2, "tx_9", null, ANTES, AGORA);

        when(processedEvents.markProcessed(eq(EVENTO), anyString(), eq(AGORA))).thenReturn(true);
        when(snapshots.findById(PEDIDO)).thenReturn(Optional.of(jaPago));

        var outcome = service.process(new ProcessOrderEventUseCase.Command(EVENTO, placed(ANTES)));

        assertThat(outcome).isEqualTo(ProcessOrderEventUseCase.Outcome.STALE_IGNORED);
        verify(snapshots, never()).save(any());
        verify(notifications, never()).save(any());
    }

    @Test
    @DisplayName("Order.Paid gera notificacao de pagamento confirmado")
    void notificacaoDependeDoEvento() {
        when(processedEvents.markProcessed(eq(EVENTO), anyString(), eq(AGORA))).thenReturn(true);
        when(snapshots.findById(PEDIDO)).thenReturn(Optional.empty());
        when(snapshots.save(any(OrderSnapshot.class))).thenAnswer(i -> i.getArgument(0));

        service.process(new ProcessOrderEventUseCase.Command(EVENTO,
                new OrderEvent.OrderPaidEvent(PEDIDO, "tx_9", new BigDecimal("919.80"), AGORA)));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.PAYMENT_CONFIRMED);
    }
}
