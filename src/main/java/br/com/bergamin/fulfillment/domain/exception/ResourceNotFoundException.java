package br.com.bergamin.fulfillment.domain.exception;

/** Recurso inexistente na projecao deste servico. Vira HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super("%s nao encontrado: %s".formatted(resource, identifier));
    }
}
