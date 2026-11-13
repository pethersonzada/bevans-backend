# Estado de retomada — 13/11/2026

## Estado atual após revalidação e editor urbano

Branches mantidas: fix/address-draft-stabilization, backend e frontend.
Base desta retomada: backend 0ce77b7, frontend bf2631f, ambos limpos.
Sem push, deploy, API/banco de produção ou alteração de credenciais reais.

Commits novos:
- Backend 6ce0ec6: migração e concorrência em PostgreSQL local real.
- Backend 9234739: consulta própria e cancelamento de solicitação com lock.
- Backend e538adc: publicação urbana, paradas e planejamento FIXA/FLEXIVEL.
- Backend 92c8bfa: fluxo HTTP integrado de publicação/solicitação/aprovação.
- Frontend 34c55c7: área separada, editor visual, serviços/tipos e testes.

### Contagens verificadas

Baseline repetida antes de alterações: backend **104**, frontend **68**.
Comparação 365b930..0ce77b7: nenhum teste removido ou filtro Surefire novo;
76 casos da base + 28 dos lotes = 104. Não localizada execução anterior de
109 em logs/documentação/histórico examinados. Não inventar causa ou cinco
regressões sem relatório daquela execução. Inventário em validacao-retomada.md.

Final: **125 testes padrão backend passaram**, sem falhas/erros/ignorados.
Perfil opt-in PostgreSQL: **14 execuções passaram**, sem falhas/erros/ignorados.
Esse perfil repete regras cobertas em H2; não somar como casos distintos.
Frontend: arquitetura, TypeScript e **72 testes passaram** (npm.cmd run check).
git diff --check passou; git ls-files target vazio. Staging/scripts auxiliares
continuam ignorados. generate-route-types.cjs é ferramenta necessária do
typecheck e usa o gerador do próprio SDK, gravando apenas .expo/types ignorado.

Logs locais: target/retomada-baseline.log, postgres-validation.log,
postgres-solicitacoes.log, postgres-publicacao.log, retomada-final.log,
frontend-editor-test.log. Evidências duráveis são os testes commitados.

### PostgreSQL e execução

Docker Desktop foi iniciado. Container descartável bevans-capacidade-validacao,
PostgreSQL 16, banco bevans_validation, bind **127.0.0.1:55435**, sem .env.
Schemas exclusivos capacity_service/publication_service e migration_UUID.
Migrações 001+002 executadas em schema novo: IDs/FK legados preservados,
capacidade nula preservada, CHECK positivo, sem publicação automática.
Container é encerrado ao terminar esta sessão; Docker Desktop não é desligado.

Maven do cache local, offline, -Dmaven.repo.local no cache do usuário e
-Dnet.bytebuddy.experimental=true no JDK local 26; pom continua Java 21.
Maven ... test executa *Test. Maven ... -Ppostgres-validation test executa
somente *PostgresIT, exigindo container local na porta acima e schemas
capacity_service/publication_service criados antes. Credencial test-only dos ITs
é exclusiva do container descartável; nunca reutilizar fora dele.

### Código entregue e issues

#35: GET /turmas/solicitacoes, DELETE /turmas/solicitacoes/{id}, DTO mínimo,
identidade da sessão, cancelamento idempotente e corrida cancelar/aprovar.
POST /rotas-publicas/{id}/solicitacoes permite ingresso sem convite privado,
somente publicada/ativa/com vagas, reutilizando AlunoTurma. Ainda não fechar
até completar dependência do catálogo #34 e jornada do passageiro.

#33/#39: RotaPublica/ParadaPublica próprias, compartilhando apenas infraestrutura
de turma/capacidade/vínculo. Não copia garagem privada. Publicação explícita,
cidade/categoria/modo, dias/horários, origem/destino públicos, paradas ordenadas
persistentes e remoção lógica. FIXA mantém ordem; VOLTA inverte; FLEXIVEL usa
OR-Tools na prévia com extremos explícitos. Prévia não grava ordem nem inclui
pedidos pendentes. Viagem ativa bloqueia alterações; iniciar usa lock da turma.
Contrato em rotas-publicas-api.md. Não há catálogo paginado/favoritos/GPS novo.

Frontend #26: Home motorista → Rotas Públicas → criar/editar. Mesmo picker e
campo único de passageiros; pin manual/drag, renomear/remover/reordenar,
horários, salvar e publicar explicitamente. Confere resposta da API. Prévia
liga pontos, explicitamente sem instruções viárias. Dados básicos e paradas
usam duas requisições: falha da segunda pode deixar dados básicos salvos;
retry usa o ID criado. Limites em frontend docs/editor-rotas-publicas.md.

Nenhuma issue foi fechada por cobertura parcial. Atualizar comentários com
os commits/resultados acima. Não anunciar aplicação completa/pronta para produção.

### Limitações e próximo passo

ADB consultado: **nenhum dispositivo/emulador conectado**. Não testados Android,
iOS, gestos reais, GPS/permissões ou jornada mobile com Firebase de testes.
Código compartilhado não substitui validar as experiências dos dois papéis.

Próxima tarefa: **backend #40 + frontend #27**, critérios já consultados:
FIXA seleciona parada; FLEXIVEL pede pin explícito; pendente não entra na rota;
aprovação/cancelamento/horário/capacidade/isolamento, antes da partida, usando
vínculos existentes sem segundo modelo de vaga. Depois #34/#42 e #19/#29
(catálogo/filtros/favoritos); #41/#28 (GPS/TTL/viagem/reconexão); excursões #29–#32
separadas, com inscrição web em bevans-webapp#14. Antes de concluir #33/#39/#26,
validar nativo e cenários adicionais de limite/conflito de edições.

---

## Histórico anterior à retomada (preservado)

## Bases e trabalho entregue

Branch local nos dois repositórios: `fix/address-draft-stabilization`.
Não houve push, deploy ou migração em produção.

Backend partiu de 365b930; frontend partiu de b149874. Ambos estavam limpos.
Não repetir a coleta completa das issues: snapshot inicial com 42 abertas,
detalhes/comentários e histórico fechado em backlog-2026-11-13.json. Consultar
atualizações desde a coleta e a issue nova backend #44.

Commits do primeiro lote:
- Backend 1bbff3a: contextLoads isolado.
- Backend ce2b9eb: rascunhos, coordenadas confirmadas, ID/proprietário, complemento.
- Backend 6634caa: auditoria e snapshot.
- Frontend 85d6968: campo único, pin manual/drag, reabertura e confirmação do PUT.

Capacidade implementada no lote seguinte, com modelos, API, lock transacional,
script incremental, cadastro/edição mobile e testes. Consultar git log para commits
finais deste lote; escopo/limites em capacidade-turmas.md. Endereços e capacidade
não concluem todo o projeto.

## Verificações

Frontend inicial: 58 testes; primeiro lote: 65; capacidade: 68. npm.cmd run check
inclui arquitetura, TypeScript e testes, todos passaram. Mocks de Mapbox não
substituem development build em dispositivo.

Backend primeiro lote: 91 testes; capacidade: **104 testes passaram**. Acrescenta testes unitários, HTTP,
transações concorrentes H2 e smoke do script em H2 PostgreSQL mode. Log final
local: target/capacidade-final.log. Não registrar PostgreSQL como validado.

Maven Wrapper falha neste sandbox; usar Maven do cache local, modo offline e
-Dmaven.repo.local apontando ao cache do usuário. JDK disponível é 26; para
Mockito atual usar -Dnet.bytebuddy.experimental=true. pom continua Java 21.
Cache/native OR-Tools exigiu execução local fora da restrição do sandbox.

## Issues e próximos passos

#44 backend criada; #44/#6 backend e #10/#25 frontend receberam comentários de
implementação/testes/commits. #35 backend/#20 frontend recebem progresso de
capacidade. Nenhuma issue foi fechada por implementação parcial.

1. Validar script/concorrência em PostgreSQL isolado. Docker CLI existe, daemon
   não está acessível. Não ligar teste à configuração de produção.
2. Validar Android/iOS: endereço/garagem/destino, reinício, IDA/VOLTA e assentos
   A=12/B=18, edição A=15, aprovação e erros. Sem dispositivo nesta execução.
3. Completar #35: cancelamento próprio/consulta de solicitação e ligação ao
   catálogo. Não prometer catálogo público porque capacidade privada existe.
4. #33/#39 backend: publicação explícita, cidade/categoria/FIXA/FLEXIVEL/paradas.
   Frontend #26, épicos backend #43/frontend #30. Depois catálogo #34, embarque
   #40, favoritos #42 e interfaces #19/#27/#29.
5. Rastreamento #41/foreground e reconexão #6/#28 frontend; identidade da viagem,
   TTL e privacidade; posição própria #18 exige DTO seguro, não liberar otimização.
6. Excursões #29/#30/#31/#32: entidade independente, convite, capacidade/check-in.
   Inscrição web pertence ao terceiro repositório bevans-webapp#14, não duplicar.

Escrita frontend fica fora da raiz liberada do sandbox: scripts temporários em
target prepararam arquivos e manifest com originais; cópia só ocorreu após
comparação para preservar mudanças concorrentes. Scripts temporários não são
necessários para executar o produto e não foram commitados.
