package br.com.bergamin.fulfillment.infrastructure.adapter.out.partner;

import br.com.bergamin.fulfillment.domain.exception.NotificationDeliveryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simula o parceiro externo de logistica.
 *
 * <p>Substitui o cliente HTTP real. A integracao verdadeira teria um {@code RestClient}
 * apontando para o ERP do parceiro -- o que muda e apenas esta classe; o contrato
 * ({@code NotificationSenderPort}), o circuit breaker e a politica de retentativa
 * continuam iguais.</p>
 *
 * <p>O comportamento e controlado por configuracao, e nao aleatorio: teste que depende de
 * sorteio falha uma vez a cada tantas execucoes e ninguem descobre por que. A demonstracao
 * e os testes derrubam e levantam o parceiro chamando {@link #setAvailable(boolean)}.</p>
 */
@Component
public class PartnerSimulator {

    private final AtomicBoolean available;
    private final long latencyMillis;

    public PartnerSimulator(@Value("${fulfillment.partner.available:true}") boolean available,
                            @Value("${fulfillment.partner.latency-millis:0}") long latencyMillis) {
        this.available = new AtomicBoolean(available);
        this.latencyMillis = latencyMillis;
    }

    public void call() {
        if (latencyMillis > 0) {
            sleep();
        }
        if (!available.get()) {
            throw new NotificationDeliveryException("parceiro respondeu HTTP 503 (indisponivel)");
        }
    }

    public void setAvailable(boolean value) {
        available.set(value);
    }

    public boolean isAvailable() {
        return available.get();
    }

    private void sleep() {
        try {
            Thread.sleep(latencyMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotificationDeliveryException("entrega interrompida", e);
        }
    }
}
