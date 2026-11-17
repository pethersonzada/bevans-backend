BEGIN;
-- Viagens antigas mantêm modalidade nula: não deduzir modalidade retroativamente.
ALTER TABLE viagem ADD COLUMN modalidade varchar(10);
ALTER TABLE viagem ADD COLUMN rota_publica_id bigint;
ALTER TABLE viagem ADD CONSTRAINT fk_viagem_rota_publica
  FOREIGN KEY (rota_publica_id) REFERENCES rotas_publicas(id);
ALTER TABLE viagem ADD CONSTRAINT ck_viagem_modalidade
  CHECK ((modalidade IS NULL AND rota_publica_id IS NULL)
    OR (modalidade = 'PRIVADA' AND rota_publica_id IS NULL)
    OR (modalidade = 'PUBLICA' AND rota_publica_id IS NOT NULL));
COMMIT;
