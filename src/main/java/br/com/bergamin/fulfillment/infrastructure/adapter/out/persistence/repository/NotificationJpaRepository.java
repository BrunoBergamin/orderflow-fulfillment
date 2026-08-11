package br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.repository;

import br.com.bergamin.fulfillment.infrastructure.adapter.out.persistence.entity.NotificationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationJpaRepository
        extends JpaRepository<NotificationJpaEntity, UUID>, JpaSpecificationExecutor<NotificationJpaEntity> {

    /**
     * Reserva o proximo lote de notificacoes vencidas.
     *
     * <p>{@code SKIP LOCKED} permite varias instancias despachando em paralelo sem que duas
     * peguem a mesma linha. Quem chama ainda empurra o {@code next_attempt_at} para frente
     * (lease), para que a linha nao seja repescada enquanto a entrega esta em andamento --
     * o lock do banco morre no fim da transacao, bem antes de a chamada externa terminar.</p>
     */
    @Query(value = """
            SELECT * FROM notification
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<NotificationJpaEntity> lockDueBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);

    long countByOrderId(UUID orderId);
}
