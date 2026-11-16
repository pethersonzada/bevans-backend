# Permissões PostgreSQL após migrações

Correção da issue #57.

As migrações podem criar objetos com uma role administrativa
diferente da role utilizada pelo Spring Boot.

Após cada migração, aplicar o script de concessão de
privilégios com os parâmetros app_role, owner_role e
schema_name, usando uma conexão administrativa autorizada.

Exemplo operacional, não executar automaticamente
em produção:

    psql -X -v ON_ERROR_STOP=1 -d vanapp \
      -v app_role=vanapp_user \
      -v owner_role=postgres \
      -v schema_name=public \
      -f scripts/sql/grant-app-permissions.sql

A role da aplicação recebe acesso ao schema, operações
SELECT/INSERT/UPDATE/DELETE em tabelas e USAGE/SELECT
em sequências.

Privilégios padrão abrangem novos objetos criados pela
owner_role especificada. Outras roles proprietárias
precisam de configuração própria.

Testar em PostgreSQL isolado antes de produção.
Preservar backup e plano de restauração.
Não executar DROP ou ALTER de tabelas em rollback.

Teste automatizado:

    bash scripts/test-db-permissions.sh

O CI deve executar o teste em banco independente.
