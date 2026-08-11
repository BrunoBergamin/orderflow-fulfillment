package br.com.bergamin.fulfillment.application.port.in;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.domain.model.OrderSnapshot;
import br.com.bergamin.fulfillment.domain.model.OrderStatus;

import java.util.UUID;

/** Caso de uso: consultar a projecao de pedidos mantida por este servico. */
public interface FindOrderSnapshotUseCase {

    OrderSnapshot findById(UUID orderId);

    PagedResult<OrderSnapshot> search(Filter filter, PageQuery pageQuery);

    /** Campos nulos nao filtram. */
    record Filter(UUID customerId, OrderStatus status) {

        public static Filter none() {
            return new Filter(null, null);
        }
    }
}
