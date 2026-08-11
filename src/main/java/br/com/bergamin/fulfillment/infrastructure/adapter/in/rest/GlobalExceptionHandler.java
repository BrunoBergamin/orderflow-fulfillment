package br.com.bergamin.fulfillment.infrastructure.adapter.in.rest;

import br.com.bergamin.fulfillment.domain.exception.InvalidMessageStateException;
import br.com.bergamin.fulfillment.domain.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** Erros em RFC 7807, no mesmo formato do servico de pedidos. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://orderflow.dev/errors/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Recurso nao encontrado", e.getMessage(), "recurso-nao-encontrado");
    }

    @ExceptionHandler(InvalidMessageStateException.class)
    public ProblemDetail handleInvalidMessageState(InvalidMessageStateException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Operacao nao permitida",
                e.getMessage(), "estado-invalido");
        problem.setProperty("currentStatus", e.getCurrentStatus().name());
        return problem;
    }

    /** Enum ou UUID invalido na query string. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "Parametro invalido",
                "Valor invalido para o parametro '%s'.".formatted(e.getName()), "parametro-invalido");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            IllegalArgumentException.class})
    public ProblemDetail handleValidation(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida", e.getMessage(), "validacao");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        String errorId = UUID.randomUUID().toString();
        log.error("erro inesperado [errorId={}]", errorId, e);

        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado. Informe o errorId ao suporte.", "erro-interno");
        problem.setProperty("errorId", errorId);
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
