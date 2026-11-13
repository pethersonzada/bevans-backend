BEGIN;
CREATE INDEX IF NOT EXISTS idx_catalogo_cidade_categoria_id
    ON rotas_publicas(translate(lower(cidade),'áàâãäéèêëíìîïóòôõöúùûüçñýÿ','aaaaaeeeeiiiiooooouuuucnyy'),categoria,id)
    WHERE publicada AND ativa;
CREATE INDEX IF NOT EXISTS idx_catalogo_publicadas_id ON rotas_publicas(id) WHERE publicada AND ativa;
COMMIT;
-- Sem extensão PostgreSQL e sem alterar dados. Recuperação mantém índices.
