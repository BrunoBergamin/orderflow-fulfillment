package br.com.bergamin.fulfillment.infrastructure.adapter.in.messaging;

import br.com.bergamin.fulfillment.application.port.out.FailedMessageRepositoryPort;
import br.com.bergamin.fulfillment.domain.model.FailedMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

/**
 * Consome a DLQ e registra cada mensagem morta no banco.
 *
 * <p>Sem isto a mensagem fica parada num topico que ninguem abre, e a unica forma de saber
 * que existe e alguem lembrar de olhar. Persistida, ela aparece na API, tem contador no
 * Prometheus e pode ser reenviada ou descartada com motivo.</p>
 *
 * <p>Este consumidor nao tem tratamento de erro proprio de proposito. Ele so grava uma
 * linha; se essa gravacao falhar, o problema e o banco, e reprocessar a mensagem quando o
 * banco voltar e o comportamento correto.</p>
 */
@Component
@ConditionalOnProperty(name = "fulfillment.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);

    private final FailedMessageRepositoryPort failedMessages;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public DeadLetterListener(FailedMessageRepositoryPort failedMessages,
                              MeterRegistry meterRegistry,
                              Clock clock) {
        this.failedMessages = failedMessages;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${fulfillment.messaging.topic}.DLT",
            groupId = "${fulfillment.messaging.consumer-group}-dlq")
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        String eventType = header(record, "eventType");

        FailedMessage message = FailedMessage.received(
                uuidHeader(record, "eventId"),
                eventType,
                record.key(),
                record.value(),
                header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
                header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                intHeader(record, KafkaHeaders.DLT_ORIGINAL_PARTITION),
                longHeader(record, KafkaHeaders.DLT_ORIGINAL_OFFSET),
                header(record, "traceId"),
                clock.instant());

        failedMessages.save(message);

        Counter.builder("fulfillment.messages.dead_lettered")
                .tag("eventType", eventType == null ? "desconhecido" : eventType)
                .description("Mensagens que cairam na DLQ")
                .register(meterRegistry)
                .increment();

        log.error("mensagem registrada na DLQ com id {} (evento {}): {}",
                message.getId(), message.getEventId(), message.getErrorMessage());
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private UUID uuidHeader(ConsumerRecord<String, String> record, String name) {
        String value = header(record, name);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            // Cabecalho invalido e uma das causas de a mensagem estar aqui: registra sem ele.
            return null;
        }
    }

    /** O Spring grava particao e offset como bytes crus, nao como texto. */
    private Integer intHeader(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        if (header == null || header.value().length < Integer.BYTES) {
            return null;
        }
        return ByteBuffer.wrap(header.value()).getInt();
    }

    private Long longHeader(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        if (header == null || header.value().length < Long.BYTES) {
            return null;
        }
        return ByteBuffer.wrap(header.value()).getLong();
    }
}
