-- Trace da requisicao que originou o evento, recebido no cabecalho da mensagem.
--
-- Guardar aqui responde a pergunta que aparece quando algo da errado: "o cliente reclamou
-- deste pedido, o que aconteceu?". Com o trace da requisicao original gravado nos dois
-- servicos, uma busca por ele traz a historia inteira. Da chamada HTTP ate a notificacao
-- entregue ao parceiro, horas depois.
ALTER TABLE processed_event ADD COLUMN trace_id VARCHAR(64);

CREATE INDEX idx_processed_event_trace ON processed_event (trace_id) WHERE trace_id IS NOT NULL;
