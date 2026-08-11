package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.repository;

import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity.ProcessedEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, UUID> {
}
