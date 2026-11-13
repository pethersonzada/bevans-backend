# Revalidação dos lotes — 13/11/2026

## Contagem reproduzida

Na retomada de 0ce77b7, `mvn ... test` executou **104 testes**, sem erros,
falhas ou ignorados. Há 17 suítes concretas compiladas e 17 no relatório;
nenhuma classe de teste obsoleta a mais em target/test-classes. Comparação
365b930..0ce77b7 não remove métodos de teste nem altera includes/excludes do
Surefire no pom. A base tinha 76 casos; os dois lotes acrescentaram 28.

| Suíte inicial da retomada | Casos |
| --- | ---: |
| CapacidadeMigrationTest | 1 |
| PresencaRotaIntegrationTest | 2 |
| TurmaCadastroIntegrationTest | 1 |
| TurmaCapacidadeConcorrenciaTest | 3 |
| AccessPolicyTest | 6 |
| ConvitePermissoesHttpTest | 8 |
| CorsSegurancaHttpTest | 3 |
| RotaAutorizacaoHttpTest | 6 |
| RotaPermissoesExtrasHttpTest | 9 |
| TurmaAutorizacaoHttpTest | 10 |
| TurmaCapacidadeHttpTest | 4 |
| EnderecoServiceTest | 9 |
| RotaServiceTest | 25 |
| TurmaCadastroTest | 5 |
| TurmaCapacidadeTest | 5 |
| TurmaServiceSegurancaTest | 6 |
| VanappBackendApplicationTests | 1 |

Não foi encontrada evidência de uma execução anterior de 109 nos logs locais,
documentação ou histórico de testes examinado. Não atribuir essa diferença a
cinco regressões ou a cinco testes removidos sem o relatório dessa execução.
O conjunto de 104 está reproduzido; execuções com filtros não equivalem à suíte
completa. Futuras contagens devem separar suíte padrão e PostgreSQL opt-in.

## PostgreSQL real

Docker Desktop local foi iniciado. PostgreSQL 16 descartável foi criado no
container bevans-capacidade-validacao, banco bevans_validation, porta exclusiva
127.0.0.1:55435. Não foi lido .env nem acessada produção. Senha test-only no teste
é exclusiva deste container, sem uso fora do ambiente descartável.

`mvn ... -Ppostgres-validation test`: **5 testes passaram**, nenhum ignorado:

- Migração SQL real em schema novo, com IDs e FK preexistentes; capacidade legada
  permaneceu nula; CHECK rejeitou zero; A=12/B=18, editar A=15 preservou B=18.
- Criação e edição pelos services JPA, seguida de nova consulta em PostgreSQL.
- Duas aprovações simultâneas na última vaga: apenas uma aprovada.
- Redução vs aprovação simultânea: ocupação nunca excedeu capacidade.
- Pedidos simultâneos por código: apenas um vínculo novo.

O perfil usa *PostgresIT; surefire padrão continua executando *Test. Os cinco
testes PostgreSQL foram adicionados nesta retomada; sua existência não explica
retroativamente o número 109 citado. JPA usa schema exclusivo capacity_service;
migração usa schema aleatório próprio. Não há DROP de schema nem de banco.

## Frontend e temporários

`npm.cmd run check`: arquitetura, TypeScript e **68 testes passaram** na retomada.
Cadastro/edição de endereços do passageiro e seleção de origem/destino do motorista
importam o mesmo EnderecoMapaPicker, que usa o mesmo EnderecoSearchInput.
Testes simulam campo único, precisão, pin manual/drag, limpeza e resposta da API.
Isto confirma o compartilhamento e os contratos testados; ainda falta executar
gestos/GPS/permissões em development build Android/iOS para os dois papéis.

target contém staging, scripts auxiliares e logs do lote anterior. `git ls-files
target` não retornou arquivos; `git check-ignore target/prepare-address.cjs`
confirmou exclusão. Nenhum auxiliar será adicionado com git add indiscriminado.
Somente testes reproduzíveis e documentação ficam versionados; staging não faz
parte do produto. Não houve remoção de alterações/arquivos locais do usuário.
