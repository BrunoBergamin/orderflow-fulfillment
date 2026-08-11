package br.com.bergamin.fulfillment.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Autenticacao por chave de API.
 *
 * <p>Este servico e interno: quem o consome sao outros sistemas, nao pessoas. Chave de API
 * e o mecanismo adequado -- JWT com perfil de usuario aqui seria resolver um problema que
 * nao existe, ja que nao ha usuario final para autorizar.</p>
 *
 * <p>A comparacao usa {@link MessageDigest#isEqual}, que percorre os bytes por inteiro
 * independentemente de onde esta a primeira diferenca. Um {@code equals} comum retorna mais
 * rapido quanto mais cedo diverge, e essa diferenca de tempo permite descobrir a chave
 * caractere a caractere (ataque de temporizacao).</p>
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-Key";
    private static final String PROTECTED_PREFIX = "/api/";

    private final byte[] expectedKey;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(@Value("${fulfillment.security.api-key}") String apiKey, ObjectMapper objectMapper) {
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Documentacao, health e metricas ficam livres; o filtro cobre so a API de dados.
        return !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);

        if (provided == null || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expectedKey)) {
            writeUnauthorized(response, request.getRequestURI());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String path) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Envie o cabecalho X-API-Key com uma chave valida.");
        problem.setTitle("Nao autenticado");
        problem.setType(URI.create("https://orderflow.dev/errors/401"));
        problem.setInstance(URI.create(path));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
