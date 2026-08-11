package br.com.bergamin.fulfillment.infrastructure.adapter.out.messaging;

import br.com.bergamin.fulfillment.application.port.out.MessageRepublisherPort;
import br.com.bergamin.fulfillment.domain.model.FailedMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Devolve a mensagem ao topico principal, reconstruindo os cabecalhos originais. */
@Component
public class KafkaRepublisherAdapter implements MessageRepublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaRepublisherAdapter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final long sendTimeoutSeconds;

    public KafkaRepublisherAdapter(KafkaTemplate<String, String> kafkaTemplate,
                                   @Value("${fulfillment.messaging.topic}") String topic,
                                   @Value("${fulfillment.messaging.send-timeout-seconds:10}") long sendTimeoutSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    @Override
    public void republish(FailedMessage message) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, message.getAggregateId(), message.getPayload());

        // Mesmo eventId de antes: se o evento ja tiver sido aplicado, a guarda de
        // idempotencia reconhece a duplicata e nada acontece duas vezes.
        record.headers().add("eventId", message.getEventId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", message.getEventType().getBytes(StandardCharsets.UTF_8));
        if (message.getTraceId() != null) {
            record.headers().add("traceId", message.getTraceId().getBytes(StandardCharsets.UTF_8));
        }
        // Marca de reenvio manual, util para distinguir no log o que voltou da DLQ.
        record.headers().add("republished", "true".getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
            log.info("mensagem {} devolvida ao topico {}", message.getId(), topic);
        } catch (InterruptedException e) {
            // Restaura a marca de interrupcao: engoli-la faz a thread ignorar um pedido de
            // encerramento e o shutdown da aplicacao trava.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("envio interrompido", e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "falha ao devolver a mensagem %s ao topico".formatted(message.getId()), e);
        }
    }
}
