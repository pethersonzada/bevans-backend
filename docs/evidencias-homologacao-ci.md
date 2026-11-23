# Evidências de integração sem usar a VM de produção

O workflow [Backend Tests](../.github/workflows/backend-tests.yml) sobe um **PostgreSQL 16 descartável** do próprio GitHub Actions, executa as suítes Java e valida migrações, operações e autorização em schemas de ensaio. Não conecta à VM Oracle nem lê o `.env` da API publicada.

## Como obter as evidências

1. Abra **Actions → Backend Tests** no repositório e selecione a execução da revisão aprovada. O workflow roda automaticamente nos PRs e no push da `main`, ou manualmente via `workflow_dispatch`.
2. Confirme que os passos **Executar testes**, **Validar PostgreSQL e migrações**, **Validar privilégios da role da aplicação**, **Registrar evidências do PostgreSQL descartável** passaram.
3. Baixe o artefato `evidencias-backend-postgresql-descartavel` e consulte `resultado.json`.
4. Registre o SHA de 40 caracteres, URL da execução e os totais no aceite da issue de qualidade.

O relatório usa os XML JUnit emitidos pelas suítes; exige que as suites HTTP `CorsSegurancaHttpTest` e `OperacaoHttpTest`, e as suites PostgreSQL `AutenticacaoPerfilPostgresIT`, `OperacaoPostgresIT`, `TurmaCapacidadePostgresIT` e `UpgradeMainSchemaPostgresIT` estejam presentes. Recusa testes ignorados ou com falhas e **não publica XML bruto, tokens ou registros pessoais**.

## O que isso comprova

- Regras e autorização de API em testes HTTP simulados, sem identidade Firebase real.
- Integrações do backend com PostgreSQL real e descartável, incluindo migrações, acesso a dados e concorrência exercitados pelas suítes existentes.
- Integridade dos relatórios (nenhuma suíte obrigatória ausente) e resultado associado ao commit exato.

## O que ainda não comprova

- Login com projeto Firebase real ou perfis provisionados no webapp.
- Execução ponta a ponta via navegador com motorista e passageiro autenticados.
- Rastreamento GPS, Mapbox, notificações push ou teste em aparelho físico.
- Homologação de termos, consentimentos e autorização de responsáveis por menores.

Essas atividades continuam nas issues de homologação do webapp. **Um relatório verde não autoriza afirmar que o produto está homologado em produção**. O banco de teste do GitHub Actions é eliminado ao término do job; não precisa de servidor novo, pagamento no Railway ou alterações no PostgreSQL da VM.
