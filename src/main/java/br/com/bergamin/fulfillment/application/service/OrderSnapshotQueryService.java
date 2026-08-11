package br.com.bergamin.fulfillment.application.service;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.application.port.in.FindOrderSnapshotUseCase;
import br.com.bergamin.fulfillment.application.port.out.OrderSnapshotCachePort;
import br.com.bergamin.fulfillment.application.port.out.OrderSnapshotRepositoryPort;
import br.com.bergamin.fulfillment.domain.exception.ResourceNotFoundException;
import br.com.bergamin.fulfillment.domain.model.OrderSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consultas da projecao, com cache-aside.
 *
 * <p>A consulta por id passa pelo cache porque e a chamada mais repetida do sistema (tela de
 * acompanhamento do pedido, polling do app). A busca com filtros vai direto ao banco: cachear
 * combinacoes arbitrarias de filtro e pagina gera muitas chaves de baixo reaproveitamento e
 * uma invalidacao que ninguem consegue manter correta.</p>
 */
@Service
public class OrderSnapshotQueryService implements FindOrderSnapshotUseCase {

    private final OrderSnapshotRepositoryPort snapshots;
    private final OrderSnapshotCachePort cache;

    public OrderSnapshotQueryService(OrderSnapshotRepositoryPort snapshots, OrderSnapshotCachePort cache) {
        this.snapshots = snapshots;
        this.cache = cache;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderSnapshot findById(UUID orderId) {
        return cache.find(orderId).orElseGet(() -> {
            OrderSnapshot fromDatabase = snapshots.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Pedido", orderId));
            cache.put(fromDatabase);
            return fromDatabase;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<OrderSnapshot> search(Filter filter, PageQuery pageQuery) {
        return snapshots.search(filter, pageQuery);
    }
}
