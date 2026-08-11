package br.com.bergamin.fulfillment.domain.exception;

/**
 * O parceiro externo nao aceitou a entrega.
 *
 * <p>Excecao, e nao valor de retorno, porque aqui a falha e mesmo excepcional: e o sinal que
 * alimenta o circuit breaker e a politica de retentativa. Compare com a recusa de pagamento
 * do servico de pedidos, que e um desfecho de negocio esperado e volta no retorno.</p>
 */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message) {
        super(message);
    }

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
