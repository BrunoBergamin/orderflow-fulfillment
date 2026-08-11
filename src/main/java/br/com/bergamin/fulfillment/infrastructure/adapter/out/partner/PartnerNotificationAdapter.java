package br.com.bergamin.fulfillment.infrastructure.adapter.out.partner;

import br.com.bergamin.fulfillment.application.port.out.NotificationSenderPort;
import br.com.bergamin.fulfillment.domain.model.Notification;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Unica porta de saida deste servico para o mundo externo -- e por isso o unico lugar com
 * circuit breaker.
 *
 * <p><b>Retentativa e disjuntor resolvem problemas diferentes e por isso convivem.</b> O
 * {@code @Retry} cobre a falha passageira: um timeout isolado, um pacote perdido. O
 * {@code @CircuitBreaker} cobre a falha sistemica: quando o parceiro esta realmente fora do
 * ar, insistir e pior do que desistir -- cada tentativa consome thread deste lado e aumenta
 * a fila do outro. Passado o limiar de erros, o circuito abre e as chamadas passam a falhar
 * de imediato, sem tocar na rede. Depois da janela de espera ele deixa passar algumas
 * chamadas de teste e, se derem certo, volta ao normal sozinho.</p>
 *
 * <p>As notificacoes recusadas nao se perdem: voltam para a tabela com o intervalo
 * exponencial calculado pelo dominio e sao entregues quando o parceiro voltar.</p>
 */
@Component
public class PartnerNotificationAdapter implements NotificationSenderPort {

    private static final Logger log = LoggerFactory.getLogger(PartnerNotificationAdapter.class);
    static final String INSTANCE = "partner";

    private final PartnerSimulator partner;

    public PartnerNotificationAdapter(PartnerSimulator partner) {
        this.partner = partner;
    }

    @Override
    @CircuitBreaker(name = INSTANCE)
    @Retry(name = INSTANCE)
    public void deliver(Notification notification) {
        partner.call();
        log.debug("notificacao {} ({}) entregue ao parceiro", notification.getId(), notification.getType());
    }
}
