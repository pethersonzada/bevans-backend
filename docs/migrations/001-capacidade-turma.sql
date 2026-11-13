-- Executar somente após backup e validação em PostgreSQL isolado.
-- Não executado automaticamente pelo aplicativo; Hibernate da base usa update.
-- Sem backfill arbitrário: NULL = capacidade ainda não configurada pelo dono.
BEGIN;
ALTER TABLE turmas ADD COLUMN IF NOT EXISTS capacidade INTEGER;
ALTER TABLE turmas ADD CONSTRAINT ck_turmas_capacidade_positiva
    CHECK (capacidade IS NULL OR capacidade > 0);
CREATE INDEX IF NOT EXISTS idx_aluno_turma_ocupacao ON aluno_turma (turma_id, status);
COMMIT;

-- Recuperação: rollback da aplicação para a versão anterior é compatível com
-- coluna/índice adicionais; preservar a coluna e seus valores. Não apagar dados.
-- Script não é repetível após sucesso (constraint nomeada); registrar execução.
