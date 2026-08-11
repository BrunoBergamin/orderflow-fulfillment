package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.repository;

import br.com.bergamin.fulfillment.domain.model.FailedMessageStatus;
import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity.FailedMessageJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FailedMessageJpaRepository extends JpaRepository<FailedMessageJpaEntity, UUID> {

    Page<FailedMessageJpaEntity> findByStatusOrderByReceivedAtDesc(FailedMessageStatus status, Pageable pageable);

    Page<FailedMessageJpaEntity> findAllByOrderByReceivedAtDesc(Pageable pageable);

    long countByStatus(FailedMessageStatus status);
}
