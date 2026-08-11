package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.application.port.in.FindNotificationUseCase;
import br.com.bergamin.fulfillment.application.port.out.NotificationRepositoryPort;
import br.com.bergamin.fulfillment.domain.model.Notification;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity.NotificationJpaEntity;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.repository.NotificationJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA das notificacoes, incluindo a reserva do lote a despachar. */
@Component
public class NotificationRepositoryAdapter implements NotificationRepositoryPort {

    private final NotificationJpaRepository repository;
    private final PersistenceMapper mapper;
    private final Duration visibilityTimeout;

    public NotificationRepositoryAdapter(NotificationJpaRepository repository,
                                         PersistenceMapper mapper,
                                         @Value("${fulfillment.dispatch.visibility-timeout-seconds:120}") long visibilityTimeoutSeconds) {
        this.repository = repository;
        this.mapper = mapper;
        this.visibilityTimeout = Duration.ofSeconds(visibilityTimeoutSeconds);
    }

    @Override
    @Transactional
    public Notification save(Notification notification) {
        NotificationJpaEntity entity = repository.findById(notification.getId())
                .map(existing -> {
                    mapper.copyToEntity(notification, existing);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(notification));
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findById(UUID notificationId) {
        return repository.findById(notificationId).map(mapper::toDomain);
    }

    /**
     * Reserva o lote e empurra a proxima tentativa para frente (lease).
     *
     * <p>O lock do banco vale so ate o fim desta transacao, que termina antes de a entrega
     * comecar. Sem empurrar o {@code next_attempt_at}, outra instancia pegaria as mesmas
     * linhas segundos depois e o parceiro receberia a notificacao duplicada. Se o processo
     * morrer no meio da entrega, o lease expira e a notificacao volta a ser tentada, e a
     * escolha consciente por "pelo menos uma vez".</p>
     */
    @Override
    @Transactional
    public List<Notification> lockDueBatch(Instant now, int batchSize) {
        List<NotificationJpaEntity> batch = repository.lockDueBatch(now, batchSize);
        if (batch.isEmpty()) {
            return List.of();
        }

        List<Notification> claimed = batch.stream().map(mapper::toDomain).toList();
        Instant leaseUntil = now.plus(visibilityTimeout);
        batch.forEach(entity -> entity.setNextAttemptAt(leaseUntil));
        return claimed;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<Notification> search(FindNotificationUseCase.Filter filter, PageQuery pageQuery) {
        Page<NotificationJpaEntity> page = repository.findAll(
                toSpecification(filter),
                PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by(Sort.Direction.DESC, "createdAt")));

        return new PagedResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                pageQuery.page(),
                pageQuery.size(),
                page.getTotalElements());
    }

    private Specification<NotificationJpaEntity> toSpecification(FindNotificationUseCase.Filter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.orderId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("orderId"), filter.orderId()));
            }
            if (filter.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.status()));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
