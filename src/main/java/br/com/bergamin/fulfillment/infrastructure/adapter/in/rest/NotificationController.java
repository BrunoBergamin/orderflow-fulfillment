package br.com.bergamin.fulfillment.infrastructure.adapter.in.rest;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.port.in.FindNotificationUseCase;
import br.com.bergamin.fulfillment.domain.model.NotificationStatus;
import br.com.bergamin.fulfillment.infrastructure.adapter.in.rest.dto.FulfillmentDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Historico de entregas.
 *
 * <p>Filtrar por {@code status=DEAD} responde a pergunta operacional que importa: o que
 * deixou de ser entregue e precisa de atencao humana.</p>
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Validated
@Tag(name = "Notificacoes", description = "Entregas ao parceiro externo, com tentativas e erros")
public class NotificationController {

    private final FindNotificationUseCase findNotification;

    public NotificationController(FindNotificationUseCase findNotification) {
        this.findNotification = findNotification;
    }

    @GetMapping
    @Operation(summary = "Lista notificacoes com filtros opcionais de pedido e status")
    public ResponseEntity<FulfillmentDtos.PageResponse<FulfillmentDtos.NotificationResponse>> search(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        var result = findNotification.search(
                new FindNotificationUseCase.Filter(orderId, status), PageQuery.of(page, size));

        return ResponseEntity.ok(FulfillmentDtos.PageResponse.from(
                result, FulfillmentDtos.NotificationResponse::from));
    }
}
