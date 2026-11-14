BEGIN;
-- Preflight só contém booleano: mensagem de falha não inclui UIDs pessoais.
-- TEMP é exclusiva da conexão e removida no commit/rollback, sem tocar dados existentes.
CREATE TEMP TABLE bevans_uid_preflight (
 sem_duplicatas boolean NOT NULL CONSTRAINT firebase_uid_duplicado_requer_reconciliacao CHECK(sem_duplicatas)
) ON COMMIT DROP;
INSERT INTO bevans_uid_preflight(sem_duplicatas)
 SELECT NOT EXISTS(SELECT 1 FROM usuarios WHERE firebase_uid IS NOT NULL GROUP BY firebase_uid HAVING count(*)>1);
CREATE UNIQUE INDEX uq_usuarios_firebase_uid ON usuarios(firebase_uid) WHERE firebase_uid IS NOT NULL;
COMMIT;
-- Índice não associa nem remove perfis legados. Em falha, manter dados e investigar em cópia isolada.
