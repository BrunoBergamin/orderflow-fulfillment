package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.application.port.in.FindOrderSnapshotUseCase;
import br.com.bergamin.fulfillment.application.port.out.OrderSnapshotRepositoryPort;
import br.com.bergamin.fulfillment.domain.model.OrderSnapshot;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity.OrderSnapshotJpaEntity;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.mapper.PersistenceMapper;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.repository.OrderSnapshotJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA da projecao de pedidos. */
@Component
public class OrderSnapshotRepositoryAdapter implements OrderSnapshotRepositoryPort {

    private final OrderSnapshotJpaRepository repository;
    private final PersistenceMapper mapper;

    public OrderSnapshotRepositoryAdapter(OrderSnapshotJpaRepository repository, PersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public OrderSnapshot save(OrderSnapshot snapshot) {
        OrderSnapshotJpaEntity entity = repository.findById(snapshot.getOrderId())
                .orElseGet(() -> new OrderSnapshotJpaEntity(snapshot.getOrderId(), snapshot.getFirstSeenAt()));
        mapper.copyToEntity(snapshot, entity);
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderSnapshot> findById(UUID orderId) {
        return repository.findById(orderId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<OrderSnapshot> search(FindOrderSnapshotUseCase.Filter filter, PageQuery pageQuery) {
        Page<OrderSnapshotJpaEntity> page = repository.findAll(
                toSpecification(filter),
                PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by(Sort.Direction.DESC, "firstSeenAt")));

        return new PagedResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                pageQuery.page(),
                pageQuery.size(),
                page.getTotalElements());
    }

    /** Monta a consulta apenas com os filtros informados. */
    private Specification<OrderSnapshotJpaEntity> toSpecification(FindOrderSnapshotUseCase.Filter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.customerId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("customerId"), filter.customerId()));
            }
            if (filter.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.status()));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
