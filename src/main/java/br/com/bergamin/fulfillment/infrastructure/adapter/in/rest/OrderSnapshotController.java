package br.com.bergamin.fulfillment.infrastructure.adapter.in.rest;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.port.in.FindOrderSnapshotUseCase;
import br.com.bergamin.fulfillment.domain.model.OrderStatus;
import br.com.bergamin.fulfillment.infrastructure.adapter.in.rest.dto.FulfillmentDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Consulta da projecao de pedidos.
 *
 * <p>Somente leitura: este servico nunca altera pedido. Estado de pedido muda no servico de
 * origem, e chega aqui pelos eventos. Expor um POST aqui criaria duas fontes de verdade.</p>
 */
@RestController
@RequestMapping("/api/v1/orders")
@Validated
@Tag(name = "Pedidos (projecao)", description = "Visao de pedidos construida a partir dos eventos")
public class OrderSnapshotController {

    private final FindOrderSnapshotUseCase findOrderSnapshot;

    public OrderSnapshotController(FindOrderSnapshotUseCase findOrderSnapshot) {
        this.findOrderSnapshot = findOrderSnapshot;
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Consulta um pedido na projecao",
            description = "Resposta servida pelo cache Redis quando disponivel.")
    public ResponseEntity<FulfillmentDtos.OrderSnapshotResponse> findById(@PathVariable UUID orderId) {
        return ResponseEntity.ok(FulfillmentDtos.OrderSnapshotResponse.from(
                findOrderSnapshot.findById(orderId)));
    }

    @GetMapping
    @Operation(summary = "Lista pedidos com filtros opcionais de cliente e status")
    public ResponseEntity<FulfillmentDtos.PageResponse<FulfillmentDtos.OrderSnapshotResponse>> search(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        var result = findOrderSnapshot.search(
                new FindOrderSnapshotUseCase.Filter(customerId, status), PageQuery.of(page, size));

        return ResponseEntity.ok(FulfillmentDtos.PageResponse.from(
                result, FulfillmentDtos.OrderSnapshotResponse::from));
    }
}
