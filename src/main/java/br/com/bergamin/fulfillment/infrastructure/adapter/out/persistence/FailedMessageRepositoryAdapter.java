package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.application.port.out.FailedMessageRepositoryPort;
import br.com.bergamin.fulfillment.domain.model.FailedMessage;
import br.com.bergamin.fulfillment.domain.model.FailedMessageStatus;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity.FailedMessageJpaEntity;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.repository.FailedMessageJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class FailedMessageRepositoryAdapter implements FailedMessageRepositoryPort {

    private final FailedMessageJpaRepository repository;

    public FailedMessageRepositoryAdapter(FailedMessageJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public FailedMessage save(FailedMessage message) {
        FailedMessageJpaEntity entity = repository.findById(message.getId())
                .map(existing -> {
                    // So a resolucao muda depois que a mensagem chega; o conteudo original
                    // e imutavel, senao o registro do que falhou perde o valor.
                    existing.setStatus(message.getStatus());
                    existing.setReprocessCount(message.getReprocessCount());
                    existing.setResolvedAt(message.getResolvedAt());
                    existing.setResolutionNote(message.getResolutionNote());
                    return existing;
                })
                .orElseGet(() -> toEntity(message));

        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FailedMessage> findById(UUID messageId) {
        return repository.findById(messageId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<FailedMessage> search(FailedMessageStatus status, PageQuery pageQuery) {
        PageRequest pageRequest = PageRequest.of(pageQuery.page(), pageQuery.size());
        Page<FailedMessageJpaEntity> page = status == null
                ? repository.findAllByOrderByReceivedAtDesc(pageRequest)
                : repository.findByStatusOrderByReceivedAtDesc(status, pageRequest);

        return new PagedResult<>(
                page.getContent().stream().map(this::toDomain).toList(),
                pageQuery.page(), pageQuery.size(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(FailedMessageStatus status) {
        return repository.countByStatus(status);
    }

    private FailedMessageJpaEntity toEntity(FailedMessage message) {
        return new FailedMessageJpaEntity(
                message.getId(), message.getEventId(), message.getEventType(),
                message.getAggregateId(), message.getPayload(), message.getErrorMessage(),
                message.getOriginalTopic(), message.getOriginalPartition(), message.getOriginalOffset(),
                message.getTraceId(), message.getReceivedAt(), message.getStatus(),
                message.getReprocessCount(), message.getResolvedAt(), message.getResolutionNote());
    }

    private FailedMessage toDomain(FailedMessageJpaEntity entity) {
        return FailedMessage.restore(
                entity.getId(), entity.getEventId(), entity.getEventType(), entity.getAggregateId(),
                entity.getPayload(), entity.getErrorMessage(), entity.getOriginalTopic(),
                entity.getOriginalPartition(), entity.getOriginalOffset(), entity.getTraceId(),
                entity.getReceivedAt(), entity.getStatus(), entity.getReprocessCount(),
                entity.getResolvedAt(), entity.getResolutionNote());
    }
}
