-- Última posição apenas, sem histórico. Aplicar antes de habilitar os endpoints.
ALTER TABLE viagem ADD COLUMN iniciada_em timestamptz;
CREATE TABLE posicao_viagem (
 viagem_id bigint PRIMARY KEY REFERENCES viagem(id),
 amostra_id varchar(80), latitude double precision, longitude double precision,
 precisao double precision, velocidade double precision,
 observado_em timestamptz, recebido_em timestamptz,
 CONSTRAINT posicao_coordenadas CHECK ((latitude IS NULL AND longitude IS NULL) OR
   (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180 AND NOT(latitude=0 AND longitude=0))),
 CONSTRAINT posicao_precisao CHECK (precisao BETWEEN 0 AND 5000),
 CONSTRAINT posicao_velocidade CHECK (velocidade BETWEEN 0 AND 100)
);
-- Recuperação: desabilitar novos endpoints; preservar tabela e relacionamentos.
