package br.com.bergamin.fulfillment.infrastructure.adapter.out.cache;

import br.com.bergamin.fulfillment.application.port.out.OrderSnapshotCachePort;
import br.com.bergamin.fulfillment.domain.model.OrderSnapshot;
import br.com.bergamin.fulfillment.domain.model.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache-aside da projecao no Redis.
 *
 * <p><b>Nenhuma falha do Redis derruba uma requisicao.</b> Toda operacao esta protegida: se
 * o cache estiver fora do ar, a leitura simplesmente vai ao banco. Um cache que consegue
 * causar erro 500 e um ponto unico de falha novo, nao uma otimizacao.</p>
 *
 * <p>A serializacao usa um record proprio em vez do objeto de dominio. Assim o formato
 * guardado no Redis nao fica preso ao construtor da classe de dominio -- refatorar o
 * dominio nao invalida (nem corrompe) o que ja esta em cache.</p>
 */
@Component
public class RedisOrderSnapshotCacheAdapter implements OrderSnapshotCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisOrderSnapshotCacheAdapter.class);
    private static final String KEY_PREFIX = "fulfillment:order:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Duration ttl;

    public RedisOrderSnapshotCacheAdapter(StringRedisTemplate redis,
                                          ObjectMapper objectMapper,
                                          MeterRegistry meterRegistry,
                                          @Value("${fulfillment.cache.ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Optional<OrderSnapshot> find(UUID orderId) {
        try {
            String json = redis.opsForValue().get(key(orderId));
            if (json == null) {
                count("miss");
                return Optional.empty();
            }
            count("hit");
            return Optional.of(objectMapper.readValue(json, CachedSnapshot.class).toDomain());
        } catch (Exception e) {
            // Cache indisponivel ou conteudo ilegivel: trata como miss e segue para o banco.
            log.warn("falha ao ler o cache do pedido {}: {}", orderId, e.getMessage());
            count("erro");
            return Optional.empty();
        }
    }

    @Override
    public void put(OrderSnapshot snapshot) {
        try {
            redis.opsForValue().set(
                    key(snapshot.getOrderId()),
                    objectMapper.writeValueAsString(CachedSnapshot.from(snapshot)),
                    ttl);
        } catch (Exception e) {
            log.warn("falha ao gravar o cache do pedido {}: {}", snapshot.getOrderId(), e.getMessage());
        }
    }

    /**
     * Invalida somente apos o commit.
     *
     * <p>Se a invalidacao acontecesse durante a transacao, uma leitura concorrente poderia
     * repovoar o cache com o valor antigo (que ainda e o visivel no banco) e esse valor
     * ficaria servido ate o TTL vencer. Com {@code afterCommit}, o cache so e limpo quando
     * o novo estado ja e o oficial. Se a transacao der rollback, nada e invalidado.</p>
     */
    @Override
    public void evictAfterCommit(UUID orderId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictNow(orderId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictNow(orderId);
            }
        });
    }

    private void evictNow(UUID orderId) {
        try {
            redis.delete(key(orderId));
        } catch (Exception e) {
            log.warn("falha ao invalidar o cache do pedido {}: {}", orderId, e.getMessage());
        }
    }

    private void count(String resultado) {
        Counter.builder("fulfillment.cache.lookup")
                .tag("resultado", resultado)
                .description("Consultas ao cache da projecao de pedidos")
                .register(meterRegistry)
                .increment();
    }

    private String key(UUID orderId) {
        return KEY_PREFIX + orderId;
    }

    /** Formato guardado no Redis, independente do modelo de dominio. */
    record CachedSnapshot(UUID orderId, UUID customerId, OrderStatus status, BigDecimal totalAmount,
                          int itemCount, String paymentTransactionId, String statusReason,
                          Instant firstSeenAt, Instant lastEventAt) {

        static CachedSnapshot from(OrderSnapshot snapshot) {
            return new CachedSnapshot(snapshot.getOrderId(), snapshot.getCustomerId(), snapshot.getStatus(),
                    snapshot.getTotalAmount(), snapshot.getItemCount(), snapshot.getPaymentTransactionId(),
                    snapshot.getStatusReason(), snapshot.getFirstSeenAt(), snapshot.getLastEventAt());
        }

        OrderSnapshot toDomain() {
            return OrderSnapshot.restore(orderId, customerId, status, totalAmount, itemCount,
                    paymentTransactionId, statusReason, firstSeenAt, lastEventAt);
        }
    }
}
