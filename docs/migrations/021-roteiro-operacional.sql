BEGIN;
-- Sem backfill: operações antigas não possuem snapshot completo recuperável.
ALTER TABLE operacoes_viagem ADD COLUMN roteiro_json text;
COMMIT;
