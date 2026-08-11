package br.com.bergamin.fulfillment.infrastructure.adapter.in.rest;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.port.in.ManageFailedMessageUseCase;
import br.com.bergamin.fulfillment.domain.model.FailedMessageStatus;
import br.com.bergamin.fulfillment.infrastructure.adapter.in.rest.dto.FailedMessageDtos;
import br.com.bergamin.fulfillment.infrastructure.adapter.in.rest.dto.FulfillmentDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Operacao da fila de mensagens mortas.
 *
 * <p>E a parte que costuma faltar: quase todo projeto configura DLQ, poucos dao a ela um
 * caminho de volta. Sem estes endpoints, a mensagem que falhou fica num topico que ninguem
 * abre.</p>
 */
@RestController
@RequestMapping("/api/v1/failed-messages")
@Validated
@Tag(name = "Mensagens mortas", description = "Inspecao e reprocessamento da DLQ")
public class FailedMessageController {

    private final ManageFailedMessageUseCase failedMessages;

    public FailedMessageController(ManageFailedMessageUseCase failedMessages) {
        this.failedMessages = failedMessages;
    }

    @GetMapping
    @Operation(summary = "Lista as mensagens que falharam",
            description = "Sem filtro traz todas; `status=PENDING` traz o que ainda precisa de decisao.")
    public ResponseEntity<FulfillmentDtos.PageResponse<FailedMessageDtos.Response>> search(
            @RequestParam(required = false) FailedMessageStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        var result = failedMessages.search(status, PageQuery.of(page, size));
        return ResponseEntity.ok(FulfillmentDtos.PageResponse.from(result, FailedMessageDtos.Response::from));
    }

    @GetMapping("/summary")
    @Operation(summary = "Quantas mensagens aguardam decisao",
            description = "Numero para monitorar: se cresce, algo esta falhando de forma sistematica.")
    public ResponseEntity<FailedMessageDtos.SummaryResponse> summary() {
        return ResponseEntity.ok(new FailedMessageDtos.SummaryResponse(failedMessages.countPending()));
    }

    @GetMapping("/{messageId}")
    @Operation(summary = "Detalha uma mensagem, com o payload e o erro original")
    public ResponseEntity<FailedMessageDtos.Response> findById(@PathVariable UUID messageId) {
        return ResponseEntity.ok(FailedMessageDtos.Response.from(failedMessages.findById(messageId)));
    }

    @PostMapping("/{messageId}/reprocess")
    @Operation(summary = "Devolve a mensagem ao topico principal",
            description = """
                    Reenviada com o mesmo `eventId`. Se o evento ja tiver sido aplicado antes da
                    falha, a guarda de idempotencia reconhece a duplicata e nada acontece duas vezes.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensagem reenviada"),
            @ApiResponse(responseCode = "409", description = "Mensagem ja resolvida, ou sem eventId para reenviar")
    })
    public ResponseEntity<FailedMessageDtos.Response> reprocess(@PathVariable UUID messageId) {
        return ResponseEntity.ok(FailedMessageDtos.Response.from(failedMessages.reprocess(messageId)));
    }

    @PostMapping("/{messageId}/discard")
    @Operation(summary = "Descarta a mensagem com um motivo registrado",
            description = "Caminho para o que reenviar nao resolveria, como payload corrompido.")
    public ResponseEntity<FailedMessageDtos.Response> discard(
            @PathVariable UUID messageId,
            @RequestBody(required = false) FailedMessageDtos.DiscardRequest request) {

        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(FailedMessageDtos.Response.from(failedMessages.discard(messageId, reason)));
    }
}
