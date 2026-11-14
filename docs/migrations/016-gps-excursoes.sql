BEGIN;
CREATE TABLE posicao_execucao_excursao (
 execucao_id bigint PRIMARY KEY REFERENCES execucoes_excursao(id),
 amostra_id varchar(80),latitude double precision,longitude double precision,precisao double precision,velocidade double precision,
 observado_em timestamptz,recebido_em timestamptz,
 CHECK((latitude IS NULL AND longitude IS NULL) OR(latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180 AND NOT(latitude=0 AND longitude=0))),
 CHECK((amostra_id IS NULL AND latitude IS NULL AND longitude IS NULL AND precisao IS NULL AND velocidade IS NULL AND observado_em IS NULL AND recebido_em IS NULL) OR
 (amostra_id IS NOT NULL AND latitude IS NOT NULL AND longitude IS NOT NULL AND precisao IS NOT NULL AND observado_em IS NOT NULL AND recebido_em IS NOT NULL)),
 CHECK(precisao BETWEEN 0 AND 5000),CHECK(velocidade BETWEEN 0 AND 100)
);
COMMIT;
-- Recuperação: desabilitar endpoints; preservar execução, IDs e tabela. Sem histórico GPS.
