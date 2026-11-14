BEGIN;
CREATE TABLE contatos_motorista (
 motorista_id bigint PRIMARY KEY REFERENCES usuarios(id),telefone_comercial varchar(15),habilitado boolean NOT NULL DEFAULT false,
 consentimento_em timestamptz,atualizado_em timestamptz NOT NULL,versao bigint NOT NULL DEFAULT 0,
 CHECK(NOT habilitado OR(telefone_comercial IS NOT NULL AND consentimento_em IS NOT NULL)),
 CHECK(telefone_comercial IS NULL OR telefone_comercial ~ '^55[1-9][0-9]([2-5][0-9]{7}|9[0-9]{8})$')
);
COMMIT;
-- Sem cópia de telefones particulares existentes. Rollback desabilita contato e preserva a tabela.
