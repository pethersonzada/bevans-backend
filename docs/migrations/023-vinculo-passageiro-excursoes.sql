BEGIN;

ALTER TABLE inscricoes_excursao
    ADD COLUMN passageiro_id bigint;

ALTER TABLE inscricoes_excursao
    ADD CONSTRAINT fk_inscricao_excursao_passageiro
    FOREIGN KEY (passageiro_id) REFERENCES usuarios(id);

CREATE INDEX idx_inscricoes_excursao_passageiro
    ON inscricoes_excursao(passageiro_id, id DESC)
    WHERE passageiro_id IS NOT NULL;

COMMIT;
