package br.com.bergamin.fulfillment.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fulfillmentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OrderFlow Fulfillment API")
                        .version("1.0.0")
                        .description("""
                                Servico de pos-venda orientado a eventos. Consome os eventos publicados
                                pelo **OrderFlow**, mantem uma projecao de leitura dos pedidos e entrega
                                notificacoes ao parceiro de logistica com retentativa e circuit breaker.

                                Autenticacao por cabecalho `X-API-Key`.
                                """)
                        .contact(new Contact()
                                .name("Bruno Alves Bergamin")
                                .url("https://www.linkedin.com/in/bruno-alves-bergamin-6b711a347"))
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes("apiKey",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")));
    }
}
