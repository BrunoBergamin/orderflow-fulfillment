package br.com.bergamin.fulfillment.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base dos testes de integracao: PostgreSQL e Redis reais.
 *
 * <p>Os containers sao iniciados uma unica vez para toda a suite. Cada classe de teste que
 * precisa de Kafka acrescenta {@code @EmbeddedKafka} -- broker em processo, sem um terceiro
 * container, o que mantem a suite leve o suficiente para rodar em maquina modesta e no
 * runner do CI.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("fulfillment")
                    .withUsername("fulfillment")
                    .withPassword("fulfillment");

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    protected void limparTudo() {
        jdbcTemplate.execute("TRUNCATE TABLE notification, order_snapshot, processed_event CASCADE");
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    protected long contarNotificacoes() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification", Long.class);
        return total == null ? 0 : total;
    }

    protected long contarEventosProcessados() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_event", Long.class);
        return total == null ? 0 : total;
    }
}
