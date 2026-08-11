package br.com.bergamin.fulfillment.application.common;

/**
 * Paginacao pedida pelo caso de uso, sem depender de Spring Data.
 *
 * <p>Duplicado de proposito em relacao ao servico de pedidos. Extrair um "commons"
 * compartilhado entre servicos parece economia, mas recria o acoplamento que a separacao
 * existia para eliminar: qualquer mudanca no jar comum vira release coordenado dos dois.</p>
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page nao pode ser negativa");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size deve estar entre 1 e " + MAX_SIZE);
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }
}
