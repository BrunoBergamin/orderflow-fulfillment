package br.com.bergamin.fulfillment.infrastructure.adapter.in.messaging;

import br.com.bergamin.fulfillment.application.port.in.ProcessOrderEventUseCase;
import br.com.bergamin.fulfillment.domain.event.OrderEvent;
import br.com.bergamin.fulfillment.domain.exception.UnparseableEventException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Adaptador de entrada por mensageria.
 *
 * <p>Faz o minimo: le os cabecalhos, delega a traducao ao parser e chama o caso de uso.
 * Nenhuma regra mora aqui -- o mesmo caso de uso poderia ser acionado por um endpoint de
 * reprocessamento sem nada mudar.</p>
 *
 * <p>Excecao lancada daqui nao e engolida: e ela que informa ao Spring Kafka se a mensagem
 * deve ser retentada ou mandada para a DLQ. Um {@code catch} generico com log aqui
 * significaria confirmar o offset de mensagens que nunca foram processadas.</p>
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);
    private static final String HEADER_EVENT_ID = "eventId";
    private static final String HEADER_EVENT_TYPE = "eventType";

    private final ProcessOrderEventUseCase processOrderEvent;
    private final OrderEventJsonParser parser;
    private final MeterRegistry meterRegistry;

    public OrderEventsListener(ProcessOrderEventUseCase processOrderEvent,
                               OrderEventJsonParser parser,
                               MeterRegistry meterRegistry) {
        this.processOrderEvent = processOrderEvent;
        this.parser = parser;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "${fulfillment.messaging.topic}",
            groupId = "${fulfillment.messaging.consumer-group}")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        UUID eventId = requiredUuidHeader(record, HEADER_EVENT_ID);
        String eventType = requiredHeader(record, HEADER_EVENT_TYPE);

        OrderEvent event = parser.parse(eventType, record.value());
        ProcessOrderEventUseCase.Outcome outcome =
                processOrderEvent.process(new ProcessOrderEventUseCase.Command(eventId, event));

        Counter.builder("fulfillment.events.consumed")
                .tag("eventType", eventType)
                .tag("outcome", outcome.name())
                .description("Eventos de pedido consumidos")
                .register(meterRegistry)
                .increment();

        log.debug("evento {} (id {}) resultou em {}", eventType, eventId, outcome);
    }

    private String requiredHeader(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        if (header == null) {
            // Sem retentativa: um cabecalho que nao veio nao vai aparecer na proxima tentativa.
            throw new UnparseableEventException("cabecalho obrigatorio ausente: " + name);
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private UUID requiredUuidHeader(ConsumerRecord<String, String> record, String name) {
        String value = requiredHeader(record, name);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new UnparseableEventException("cabecalho %s nao e um UUID: %s".formatted(name, value), e);
        }
    }
}
