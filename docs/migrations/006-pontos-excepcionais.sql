-- Incremental: rotas existentes continuam bloqueando excecoes FIXAS.
ALTER TABLE rotas_publicas ADD COLUMN permite_ponto_excepcional boolean NOT NULL DEFAULT false;
-- Recuperacao: manter coluna e desabilitar a opcao na API, sem remover dados.
