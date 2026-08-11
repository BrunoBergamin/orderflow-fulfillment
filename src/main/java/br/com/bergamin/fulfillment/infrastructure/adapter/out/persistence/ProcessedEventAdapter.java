package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence;

import br.com.bergamin.fulfillment.application.port.out.ProcessedEventPort;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity.ProcessedEventJpaEntity;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.repository.ProcessedEventJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Controle de idempotencia apoiado na chave primaria da tabela.
 *
 * <p>{@code Propagation.MANDATORY}: marcar o evento como processado <b>tem</b> que estar na
 * mesma transacao que aplica o efeito dele. Em transacoes separadas, um erro no meio
 * deixaria o evento marcado como consumido sem que a projecao ou a notificacao existissem --
 * e a reentrega do Kafka seria ignorada, perdendo o fato para sempre.</p>
 *
 * <p>A checagem previa resolve o caso comum. Duas entregas simultaneas do mesmo evento sao
 * praticamente impossiveis (a mesma mensagem cai sempre na mesma particao, atendida por um
 * unico consumidor); se ainda assim ocorresse, a chave primaria aborta a transacao, o Kafka
 * reentrega e a segunda passagem cai no caminho de duplicata. Correto nos dois cenarios.</p>
 */
@Component
public class ProcessedEventAdapter implements ProcessedEventPort {

    private final ProcessedEventJpaRepository repository;

    public ProcessedEventAdapter(ProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(UUID eventId, String eventType, Instant processedAt) {
        if (repository.existsById(eventId)) {
            return false;
        }
        repository.save(new ProcessedEventJpaEntity(eventId, eventType, processedAt));
        return true;
    }
}
