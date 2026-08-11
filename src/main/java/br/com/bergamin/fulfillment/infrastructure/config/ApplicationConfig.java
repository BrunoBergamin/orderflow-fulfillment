package br.com.bergamin.fulfillment.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class ApplicationConfig {

    /**
     * Relogio injetavel.
     *
     * <p>Indispensavel aqui: a politica de retentativa e baseada em tempo. Com o
     * {@code Clock} injetado, o teste avanca o relogio e verifica o intervalo exato entre
     * tentativas. Sem ele, so restaria colocar o teste para dormir.</p>
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
