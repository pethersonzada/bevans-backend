BEGIN;
ALTER TABLE operacoes_viagem
    ADD COLUMN conclusao_modo varchar(10),
    ADD COLUMN conclusao_motivo varchar(500),
    ADD COLUMN conclusao_autor varchar(80),
    ADD COLUMN conclusao_em timestamp with time zone,
    ADD COLUMN conclusao_gps_estado varchar(30),
    ADD COLUMN conclusao_latitude double precision,
    ADD COLUMN conclusao_longitude double precision,
    ADD COLUMN conclusao_precisao double precision,
    ADD COLUMN conclusao_gps_observado_em timestamp with time zone,
    ADD COLUMN conclusao_distancia_metros double precision;
COMMIT;
