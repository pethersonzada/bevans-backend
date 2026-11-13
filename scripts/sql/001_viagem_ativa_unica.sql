-- Permite apenas uma viagem ativa por turma.
-- Viagens finalizadas e registros excluídos não são afetados.

CREATE UNIQUE INDEX IF NOT EXISTS
    ux_viagem_ativa_por_turma
ON viagem (turma_id)
WHERE status = 'EM_ANDAMENTO'
  AND excluido = false;
