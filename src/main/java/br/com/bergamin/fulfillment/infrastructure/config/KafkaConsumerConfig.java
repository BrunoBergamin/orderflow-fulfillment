package br.com.bergamin.fulfillment.infrastructure.config;

import br.com.bergamin.fulfillment.domain.exception.UnparseableEventException;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Politica de erro do consumidor: retenta o que faz sentido, descarta o resto para a DLQ.
 *
 * <p>Sem isto, o comportamento padrao de uma excecao no listener e nao confirmar o offset e
 * reprocessar a mesma mensagem para sempre. Uma unica mensagem defeituosa trava a particao
 * inteira e nenhuma mensagem posterior e entregue -- falha silenciosa que so aparece quando
 * alguem nota que o sistema parou de reagir.</p>
 *
 * <p>A separacao e simples: falha <i>transitoria</i> (banco reiniciando, deadlock) merece
 * novas tentativas com espera crescente; falha <i>permanente</i> (JSON quebrado, tipo
 * desconhecido, cabecalho ausente) nao melhora com repeticao e vai direto para a DLQ, onde
 * pode ser inspecionada e reprocessada depois sem segurar a fila.</p>
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${fulfillment.messaging.retry.initial-interval-ms:1000}") long initialInterval,
            @Value("${fulfillment.messaging.retry.max-elapsed-ms:10000}") long maxElapsed) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                // Particao -1 deixa o Kafka escolher: o topico de DLQ nao precisa ter o
                // mesmo numero de particoes do topico de origem.
                (record, exception) -> {
                    log.error("mensagem do topico {} enviada para a DLQ apos falha: {}",
                            record.topic(), exception.getMessage());
                    return new TopicPartition(record.topic() + ".DLT", -1);
                });

        // Espera crescente entre tentativas (1s, 2s, 4s...) ate estourar a janela total.
        // Crescer o intervalo da tempo de a causa transitoria passar, em vez de martelar
        // um banco que ainda esta reiniciando.
        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval, 2.0);
        backOff.setMaxElapsedTime(maxElapsed);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(UnparseableEventException.class);
        return errorHandler;
    }
}
