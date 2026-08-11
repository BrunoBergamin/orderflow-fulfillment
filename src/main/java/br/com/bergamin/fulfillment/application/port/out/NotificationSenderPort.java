package br.com.bergamin.fulfillment.application.port.out;

import br.com.bergamin.fulfillment.domain.model.Notification;

/**
 * Porta de saida para o parceiro externo (logistica / ERP).
 *
 * <p>O adaptador que a implementa e o unico ponto do sistema que fala com fora, e por isso e
 * onde ficam o circuit breaker e a retentativa rapida do Resilience4j.</p>
 */
public interface NotificationSenderPort {

    /**
     * @throws br.com.bergamin.fulfillment.domain.exception.NotificationDeliveryException
     *         quando o parceiro recusa, nao responde ou o circuito esta aberto
     */
    void deliver(Notification notification);
}
