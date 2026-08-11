package br.com.bergamin.fulfillment.application.port.out;

import br.com.bergamin.fulfillment.domain.model.OrderSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Cache da projecao de pedidos (cache-aside).
 *
 * <p>Consultar status de pedido e leitura repetida: o cliente atualiza a tela, o app faz
 * polling, o parceiro confere. Guardar o resultado por alguns minutos tira essa carga do
 * banco.</p>
 *
 * <p>O cache e sempre descartavel: se o Redis estiver fora do ar, a consulta apenas vai ao
 * banco. Nenhuma decisao de negocio depende dele.</p>
 */
public interface OrderSnapshotCachePort {

    Optional<OrderSnapshot> find(UUID orderId);

    void put(OrderSnapshot snapshot);

    /**
     * Invalida a entrada <b>depois</b> que a transacao commitar.
     *
     * <p>A ordem importa. Invalidando antes do commit, existe uma janela em que outra
     * requisicao le o banco (ainda com o valor antigo, pois a transacao nao commitou) e
     * repovoa o cache -- que passa a servir dado velho ate o TTL expirar. Invalidar depois
     * do commit fecha essa janela.</p>
     */
    void evictAfterCommit(UUID orderId);
}
