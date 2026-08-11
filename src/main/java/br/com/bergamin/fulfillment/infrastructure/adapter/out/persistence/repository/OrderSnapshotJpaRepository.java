package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.repository;

import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity.OrderSnapshotJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * {@code JpaSpecificationExecutor} em vez de uma JPQL com {@code :param IS NULL}.
 *
 * <p>Filtro opcional escrito como "ou o parametro e nulo, ou compara" gera SQL que o
 * planejador do Postgres nao consegue otimizar bem, alem de esbarrar em inferencia de tipo
 * de parametro nulo. A Criteria API monta a consulta so com os filtros presentes.</p>
 */
public interface OrderSnapshotJpaRepository
        extends JpaRepository<OrderSnapshotJpaEntity, UUID>, JpaSpecificationExecutor<OrderSnapshotJpaEntity> {
}
