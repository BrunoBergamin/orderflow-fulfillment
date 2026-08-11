package br.com.bergamin.fulfillment.infrastructure.observability;

import org.slf4j.MDC;

/**
 * Trace da requisicao que originou o evento, propagado pelo cabecalho da mensagem.
 *
 * <p>Este servico tem o proprio trace, criado quando a mensagem chega. Ele responde "o que
 * aconteceu no consumo"; o trace de origem responde "de qual requisicao isso veio". Os dois
 * juntos costuram a historia inteira, mesmo com horas de distancia entre o pedido e a
 * entrega ao parceiro.</p>
 *
 * <p>Nao tentamos continuar o span do produtor. Para isso seria preciso propagar o contexto
 * W3C completo (traceparent com span pai) atraves da outbox, e o span pai ja terminou muito
 * antes de a mensagem sair. Guardar a referencia resolve o problema real -- achar tudo que
 * pertence a uma requisicao -- sem fingir uma relacao de causalidade que o relogio nao
 * sustenta.</p>
 */
public final class OriginTrace {

    /** Chave no MDC. Aparece automaticamente nos logs configurados com {@code %X{originTraceId}}. */
    public static final String MDC_KEY = "originTraceId";

    private OriginTrace() {
    }

    public static void set(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(MDC_KEY, traceId);
        }
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
