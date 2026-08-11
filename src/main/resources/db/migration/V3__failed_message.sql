-- Mensagens que cairam na DLQ.
--
-- Mandar para a fila de mensagens mortas desentope a particao, mas por si so nao resolve:
-- a mensagem fica em um topico que ninguem olha. Persistir aqui a torna consultavel pela
-- API e da a ela um caminho de volta. DLQ sem reprocessamento e so um lugar mais organizado
-- para perder dado.
CREATE TABLE failed_message (
    id                 UUID         PRIMARY KEY,
    -- Anulaveis de proposito: cabecalho ausente e justamente uma das causas de a mensagem
    -- ter falhado, e nesse caso ela ainda precisa ser registrada.
    event_id           UUID,
    event_type         VARCHAR(100),
    aggregate_id       VARCHAR(80),
    payload            TEXT,
    error_message      VARCHAR(1000),
    original_topic     VARCHAR(120),
    original_partition INTEGER,
    original_offset    BIGINT,
    trace_id           VARCHAR(64),
    received_at        TIMESTAMPTZ  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    reprocess_count    INTEGER      NOT NULL DEFAULT 0,
    resolved_at        TIMESTAMPTZ,
    resolution_note    VARCHAR(500)
);

-- A consulta operacional e sempre "o que ainda precisa de decisao".
CREATE INDEX idx_failed_message_pendentes ON failed_message (received_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_failed_message_evento ON failed_message (event_id) WHERE event_id IS NOT NULL;
