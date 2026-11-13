# PR #45 — integração local com main

Base do PR: `9ef969d`. Fetch de origin executado; main atualizada para
`1a24bb3c7ae198208c9a0108d889f65623a162a4` (10 commits novos).
Árvore inicialmente limpa, branch `fix/address-draft-stabilization` preservada.
Merge normal, sem rebase, force push, push, merge do PR ou acesso a produção.

## Resolução e revisão

- `RotaController`: retirado o conflito entre GPS legado em memória e GPS
  persistente por viagem. Preservadas autorização e validação de viagem ativa e
  coordenadas da main. Endpoints antigos orientam a migração por HTTP 410 usando
  o envelope JSON/EndpointLegadoException da main, sem um segundo canal de GPS.
  Mantidos 400 para amostra inválida/inatividade no POST e 404 no GET inativo.
- `ViagemService`: início e finalização mantêm lock pessimista da turma usado
  pelas aprovações e pelo GPS. Preservados RecursoNaoEncontradoException/404,
  ConflitoException/409, consulta persistente de atividade e proteção contra
  início simultâneo da main; mantidos viagemId e limpeza de GPS ao finalizar.
  Os métodos de lock do repository mantidos pelos dois lados consultam a mesma
  linha com PESSIMISTIC_WRITE; imports duplicados removidos.
- `VanappBackendApplicationTests`: combinados H2 isolado, import de .env
  desabilitado, Firebase desabilitado e SQL init desabilitado.
- Revisados merges automáticos: correções de usuário/404/conflitos e validação
  OR-Tools da main preservadas; capacidade, coordenadas confirmadas, embarques,
  catálogo, favoritos, SSE e Excursões mantidos. Firebase conserva expiração
  verificada para SSE e o novo envelope de erro de autenticação/CORS.
- Testes de ambos os lados preservados e adaptados: construtores atuais,
  fixtures com capacidade/coordenadas válidas e mocks do lock. Teste de nome sem
  coordenadas verifica confirmação no mapa, sem reintroduzir geocodificação
  silenciosa. Pedido já analisado continua conflito 409; corrida de solicitações
  reconhece ConflitoException da main sem esconder exceções inesperadas.

## Workflow

`.github/workflows/backend-tests.yml` preservado e ampliado:
`push` em main, `pull_request` sem filtro restritivo e `workflow_dispatch`.
O push manual desta branch atualiza o PR e deve disparar `pull_request/synchronize`.
Java 21 Temurin; `mvn clean verify`, PostgreSQL 16 em serviço descartável na porta
55435, schemas exclusivos e `mvn -Ppostgres-validation verify`. Senha do serviço
é literal somente de teste, sem uso de secrets de produção. Não usa
pull_request_target, deploy nem execução privilegiada de código do PR.
Ainda não há execução remota deste novo commit: depende do push manual e das
configurações de Actions do repositório. Não afirmar CI verde antes de executá-la.

## Migrações e ddl-auto

Revisadas 001–008 contra os modelos da main, não contra banco de produção.
Fixture `src/test/resources/sql/main-1a24bb3-schema.sql` reproduz os seis modelos
legados; o novo UpgradeMainSchemaPostgresIT preserva IDs e relacionamentos com
dados, aplica índice parcial de viagem ativa da main + 001–008 e valida todos
os modelos atuais com Hibernate **validate**, sem completar DDL automaticamente.
São compatíveis nesse ambiente PostgreSQL 16; também mantidos os testes SQL de
constraints, unicidade de pedidos/favoritos, capacidade e coordenadas.

- 001 adiciona capacidade nullable: legados não recebem lotação inventada.
- 002–005 criam publicações/paradas/pedidos/favoritos e índices, sem publicar
  turmas privadas ou copiar dados pessoais automaticamente.
- 006 adiciona boolean NOT NULL com DEFAULT false, seguro para rotas já existentes.
- 007 mantém início legado nulo e cria última posição, sem reinterpretar datas
  locais históricas como UTC nem gravar histórico GPS.
- 008 cria Excursões isoladas, com FKs e validações próprias.
- `scripts/sql/001_viagem_ativa_unica.sql` é separado de 001 de capacidade:
  índice único parcial da main também precisa ser registrado/aplicado. O novo
  teste comprova rejeição de segunda viagem ativa com SQLSTATE 23505.

`spring.jpa.hibernate.ddl-auto=update` foi preservado; não é executor nem ledger
dessas migrações e não garante CHECKs/índices parciais. Scripts 001, 006–008 não
são todos repetíveis; IF NOT EXISTS em outros scripts não repara uma tabela
incompleta criada previamente pelo Hibernate. Não aplicar tudo cegamente se
algum ambiente já iniciou esta versão com update.

Antes de uma futura implantação autorizada: obter backup/schema real e histórico
de scripts aplicados; conferir colunas/tipos/constraints/índices existentes e
duplicidades ativas (`SELECT turma_id,count(*) FROM viagem WHERE status =
'EM_ANDAMENTO' AND excluido=false GROUP BY turma_id HAVING count(*)>1`).
Não apagar duplicatas automaticamente. Aplicar somente incrementos pendentes
antes de iniciar código novo; validar schema e então considerar override para
`SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. Nenhuma configuração de produção mudou.
Recuperação preserva tabelas/IDs/dados adicionados e volta a versão da aplicação;
não usar DROP como rollback. DDL de 006–008 exige execução com falha imediata e
transação controlada pelo executor; não tentar repetir após aplicação parcial.

## Evidências

- `mvn clean verify`: **209 testes**, zero falhas, erros ou ignorados.
- `mvn -Ppostgres-validation verify`: **52 execuções**, zero falhas, erros ou
  ignorados, incluindo o novo upgrade completo. São repetições das regras H2
  em PostgreSQL e testes específicos de migração, não 261 casos distintos.
- Local: Maven 3.9.14 em cache; Java 26 com flag ByteBuddy experimental somente
  local. Projeto e workflow continuam Java 21. Execução Java 21 remota pendente.
- Logs ignorados: `.validation-logs/pr45-clean-verify.log` e
  `.validation-logs/pr45-postgres-verify.log`.
- `git diff --check`, busca de marcadores de conflitos e inspeção dos arquivos
  rastreados: sem frontend/JS/Expo, target, .validation-logs, .env, chaves privadas
  ou tokens reais detectados. BackendBoundaryTest também passou. Varredura por
  padrões não equivale a auditoria completa de credenciais/histórico.

Container exclusivo `bevans-pr45-validation`, bind 127.0.0.1:55435, ID
`ec2132855e83788de640776643c14fa5f694eb1c2d64f1ad72f36dc9775b9317`.
Encerrado após testes com ID conferido; --rm removeu somente esse banco de teste.
Nenhum teste foi conectado à API/banco de produção. Nenhum teste anterior removido.
Pendências funcionais anteriores (#30/#31/#32, mobile e validação SSE/Nginx real)
continuam fora deste merge de integração; não foram declaradas concluídas.
