package br.com.bergamin.fulfillment.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro dos eventos ja consumidos. O que torna o consumo idempotente.
 *
 * <p>A entrega no Kafka e "pelo menos uma vez": rebalanceamento de consumidores, timeout de
 * commit de offset ou uma reentrega do produtor fazem a mesma mensagem chegar de novo. Sem
 * este registro, o cliente receberia duas notificacoes do mesmo pedido.</p>
 *
 * <p><b>Fica no PostgreSQL, e nao no Redis, de proposito.</b> A garantia vem de um indice
 * unico participando da <i>mesma transacao</i> que grava a projecao e a notificacao: ou
 * tudo acontece, ou nada. O Redis nao entra nessa transacao e pode perder chaves (despejo
 * por memoria, reinicio sem persistencia), e uma chave perdida aqui significa notificacao
 * duplicada. Cache e para acelerar leitura, nao para garantir correcao.</p>
 */
public interface ProcessedEventPort {

    /**
     * Marca o evento como processado.
     *
     * @return {@code false} se ele ja tinha sido processado antes
     */
    boolean markProcessed(UUID eventId, String eventType, Instant processedAt);
}
