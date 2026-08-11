package br.com.bergamin.fulfillment.infrastructure.adapter.in.messaging;

import br.com.bergamin.fulfillment.domain.event.OrderEvent;
import br.com.bergamin.fulfillment.domain.exception.UnparseableEventException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Traduz o JSON publicado pelo servico de pedidos nos eventos deste servico.
 *
 * <p>A leitura e feita campo a campo, e nao por desserializacao automatica em uma classe
 * espelho. E deliberado: o contrato entre servicos e o JSON, e ler so os campos usados
 * deixa o consumidor tolerante a campos novos do produtor -- ele nao quebra quando o outro
 * time adiciona algo. O que <b>tem</b> que existir e verificado aqui, com erro explicito.</p>
 */
@Component
public class OrderEventJsonParser {

    private final ObjectMapper objectMapper;

    public OrderEventJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OrderEvent parse(String eventType, String payload) {
        JsonNode node = readTree(eventType, payload);

        return switch (eventType) {
            case OrderEvent.OrderPlacedEvent.TYPE -> new OrderEvent.OrderPlacedEvent(
                    uuid(node, "orderId"),
                    uuid(node, "customerId"),
                    money(node, "total"),
                    node.path("items").isArray() ? node.path("items").size() : 0,
                    instant(node));
            case OrderEvent.OrderPaidEvent.TYPE -> new OrderEvent.OrderPaidEvent(
                    uuid(node, "orderId"),
                    text(node, "transactionId"),
                    money(node, "amount"),
                    instant(node));
            case OrderEvent.OrderCancelledEvent.TYPE -> new OrderEvent.OrderCancelledEvent(
                    uuid(node, "orderId"),
                    text(node, "reason"),
                    node.path("paymentDeclined").asBoolean(false),
                    instant(node));
            default -> throw new UnparseableEventException("tipo de evento desconhecido: " + eventType);
        };
    }

    private JsonNode readTree(String eventType, String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new UnparseableEventException("payload invalido para o evento " + eventType, e);
        }
    }

    private UUID uuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new UnparseableEventException("campo obrigatorio ausente: " + field);
        }
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException e) {
            throw new UnparseableEventException("campo %s nao e um UUID: %s".formatted(field, value.asText()), e);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** O produtor serializa dinheiro como {@code {"amount": 123.45}}. */
    private BigDecimal money(JsonNode node, String field) {
        JsonNode value = node.path(field).path("amount");
        return value.isMissingNode() || value.isNull() ? BigDecimal.ZERO : value.decimalValue();
    }

    private Instant instant(JsonNode node) {
        JsonNode value = node.get("occurredAt");
        if (value == null || value.isNull()) {
            throw new UnparseableEventException("campo obrigatorio ausente: occurredAt");
        }
        try {
            return value.isNumber()
                    ? Instant.ofEpochMilli(value.asLong())
                    : Instant.parse(value.asText());
        } catch (Exception e) {
            throw new UnparseableEventException("occurredAt invalido: " + value.asText(), e);
        }
    }
}
