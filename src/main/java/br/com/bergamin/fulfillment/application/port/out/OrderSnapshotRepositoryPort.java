package br.com.bergamin.fulfillment.application.port.out;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.application.port.in.FindOrderSnapshotUseCase;
import br.com.bergamin.fulfillment.domain.model.OrderSnapshot;

import java.util.Optional;
import java.util.UUID;

/** Porta de saida da projecao de pedidos. */
public interface OrderSnapshotRepositoryPort {

    OrderSnapshot save(OrderSnapshot snapshot);

    Optional<OrderSnapshot> findById(UUID orderId);

    PagedResult<OrderSnapshot> search(FindOrderSnapshotUseCase.Filter filter, PageQuery pageQuery);
}
