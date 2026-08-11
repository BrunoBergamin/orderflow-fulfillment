package br.com.bergamin.fulfillment.domain.exception;

/**
 * Mensagem que este servico nao consegue interpretar (JSON quebrado, tipo desconhecido,
 * cabecalho obrigatorio ausente).
 *
 * <p>Marcada como <b>nao retentavel</b> no tratador de erros do Kafka. Retentar uma
 * mensagem envenenada e desperdicio garantido: ela vai falhar igual nas proximas mil vezes,
 * enquanto trava o avanco da particao e segura todas as mensagens boas atras dela. O
 * caminho certo e ir direto para a DLQ e liberar a fila.</p>
 */
public class UnparseableEventException extends RuntimeException {

    public UnparseableEventException(String message) {
        super(message);
    }

    public UnparseableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
