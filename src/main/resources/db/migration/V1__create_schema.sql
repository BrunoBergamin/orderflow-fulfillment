-- OrderFlow Fulfillment: schema inicial.

-- Projecao de leitura dos pedidos, montada a partir dos eventos.
-- A chave primaria e o id do pedido na origem: este servico nao cria identidade propria
-- para algo que ja tem dono em outro lugar.
CREATE TABLE order_snapshot (
    order_id               UUID          PRIMARY KEY,
    customer_id            UUID,
    status                 VARCHAR(30),
    total_amount           NUMERIC(19,2) NOT NULL DEFAULT 0,
    item_count             INTEGER       NOT NULL DEFAULT 0,
    payment_transaction_id VARCHAR(100),
    status_reason          VARCHAR(255),
    first_seen_at          TIMESTAMPTZ   NOT NULL,
    last_event_at          TIMESTAMPTZ
);

-- Fila de entregas ao parceiro externo, com estado e agendamento da proxima tentativa.
CREATE TABLE notification (
    id              UUID         PRIMARY KEY,
    order_id        UUID         NOT NULL,
    type            VARCHAR(40)  NOT NULL,
    message         VARCHAR(255),
    status          VARCHAR(20)  NOT NULL,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    CONSTRAINT notification_tentativas_nao_negativas CHECK (attempts >= 0)
);

-- Eventos ja aplicados. E o que torna o consumo idempotente: a garantia esta na chave
-- primaria do banco, participando da mesma transacao que grava o efeito do evento.
CREATE TABLE processed_event (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_order_snapshot_customer ON order_snapshot (customer_id);
CREATE INDEX idx_order_snapshot_status   ON order_snapshot (status);
CREATE INDEX idx_notification_order      ON notification (order_id);

-- Indice parcial: o despachante so procura notificacoes pendentes e vencidas. Indexar as
-- ja entregues (que serao a maioria absoluta das linhas) custaria escrita sem retorno.
CREATE INDEX idx_notification_due ON notification (next_attempt_at)
    WHERE status = 'PENDING';
