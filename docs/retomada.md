# Estado de retomada — 13/11/2026

## Estado vigente — backend apenas, após os novos lotes

Branch preservada: `fix/address-draft-stabilization`. Base desta sessão: `41c68ed`.
Nenhuma escrita no frontend, push, deploy ou acesso a produção nesta sessão.
Histórico antigo abaixo não autoriza implementar frontend dentro deste backend.

Commits locais desta sessão:
- `bb14b3b`: remoção segura dos artefatos frontend e teste de limite do repositório.
- `1f13494`: #40 embarques privados, aprovação, capacidade compartilhada e concorrência.
- `85a03ab`: #34 catálogo paginado com filtros combináveis e índices incrementais.
- `04fb12e`: #42 favoritos persistentes, isolados e idempotentes.
- `07bbedb`: teste também rejeita scripts JS/CJS/MJS indevidos no backend.
- `f7ee5e0`: #40 opt-in explícito do motorista para exceções em FIXA.
- `29837f7`: #41 GPS privado vinculado à viagem, TTL, rate limit e SSE limitado.
- `ab15abb`: teste de favoritos independente da primeira página em schema reutilizado.
- `5873819`: #29 estrutura/API própria de Excursões com autorização e versão.

### Validação executada nesta sessão

Baseline anterior: 125 testes padrão + 14 execuções PostgreSQL. Nenhum teste
anterior removido (`git diff 41c68ed --diff-filter=D --name-only -- src/test` vazio).
Último `mvn clean verify`: **176 testes aprovados**, zero falhas/erros/ignorados;
recompilação e empacotamento Spring Boot concluídos. Após a correção isolada do
teste de favoritos, os **7 testes H2** desse conjunto passaram novamente.
Perfil `postgres-validation`: **51 execuções aprovadas**, zero falhas/erros/ignorados.
Repetem cenários H2; não somar como casos únicos. Migrações incrementais **001–008**
executadas em schema PostgreSQL novo, preservando IDs/FK legados e verificando
unicidade de pedidos/favoritos e constraints novas de posição/Excursões.

Falhas intermediárias investigadas e corrigidas:
- Coluna do opt-in FIXA ausente em dois schemas locais antigos: Hibernate update
  não substitui a migração incremental. Aplicada 006 somente nesses schemas e
  reexecução de 40 testes PostgreSQL passou. Não houve alteração em produção.
- Teste de favoritos pressupunha rota nova na primeira página depois de várias
  execuções: agora usa nome único e filtro próprio, sem apagar registros.
- Erros de compilação pontuais dos novos testes/imports e um restub Mockito SSE
  foram corrigidos antes das suítes finais.

Logs ignorados em `.validation-logs/`: `cleanup-clean-verify.log`,
`backend-excecoes-clean-verify.log`, `excecoes-postgres-migrado.log`,
`gps-clean-verify.log`, `gps-postgres.log`, `excursoes-clean-verify.log`,
`excursoes-postgres-final.log`, `favoritos-repeatability.log`.
`git diff --check` passou. `git ls-files target` vazio. Teste BackendBoundaryTest
passou no build completo; nenhuma árvore Expo/React/TypeScript/JS de preparação.

### Contratos, issues e pendências reais

Backend #40: FIXA escolhe parada ou pin excepcional explicitamente permitido pelo
dono; FLEXIVEL exige pin confirmado. Pedidos não entram automaticamente na rota.
Capacidade usa o mesmo lock de turma das aprovações #35. Só dono vê planejamento
privado com pins aprovados. Cancelamento de aprovado/replanejamento continua 409.
Contrato: `embarques-api.md`; migrações 003 e 006.

Backend #34/#42: catálogo só publica rotas ativas explícitas; filtros/paginação,
vagas agregadas e favoritos por identidade Firebase, sem passageiros/endereço
privado/GPS. Contratos: `catalogo-api.md`, `favoritos-api.md`; migrações 004/005.

Backend #41: último GPS em PostgreSQL, TTL 30s, envio mínimo 5s, identidade da
viagem, idempotência, SSE 10s com expiração/revalidação e fallback polling.
Finalizar limpa GPS e bloqueia novo envio usando o mesmo lock de turma.
`/rota/status-atual` inclui viagemId; `/rota/localizacao-van` legado retorna 410
após autorização e orienta migração. Contrato: `rastreamento-api.md`; migração 007.
Pendente: cliente mobile, dispositivo/rede real/background, consentimento de
responsável para menores e política de cancelamento de viagem distinta de finalizar.
Não declarar #41 inteira concluída nem reabrir frontend neste diretório.

Backend #29: Excursao própria com CRUD do organizador, publicação e cancelamento
lógico, capacidade/datas/coordenadas e controle de versão. Não cria Turma nem
RotaPublica. Contrato: `excursoes-api.md`; migração 008. Não há inscrição pública
habilitada, conclusão automática ou compartilhamento de convites escolares.
Estado CONCLUIDA está reservado ao ciclo operacional da #31.

Issues #40/#34/#42/#41/#29 receberam comentários de implementação/testes. Nenhuma
issue frontend foi alterada/fechada. As issues backend permanecem abertas para
revisão/integração; #28–#32 foram consultadas diretamente, sem reiniciar auditoria.

### Próxima tarefa concreta

Implementar backend **#30** sobre Excursao: convites aleatórios com hash no banco,
expiração/revogação, endpoints limitados por convite (Firebase não obrigatório),
credencial individual independente para consultar/cancelar a própria inscrição,
proteção contra abuso e aprovação/reserva com lock de Excursao. Ao adicionar
inscrições, impedir edição de capacidade abaixo da ocupação e mudanças de
destino/horário que invalidem participantes. Não liberar endpoint anônimo genérico
nem reutilizar tokens escolares. Depois #31 OR-Tools/check-in auditável e #32.
#32 requer integração mobile/web em repositórios externos ao escopo desta sessão;
registrar essa limitação, sem criar frontend aqui ou declarar demo executada.

PostgreSQL descartável usado nesta sessão: container bevans-capacidade-validacao,
ID 326834d04ef608f6aa97ce8038bacb24dd2c107490ef429b83a99fbb69087c45,
bind 127.0.0.1:55435, PG16/bevans_validation. Schemas capacity_service,
publication_service, boarding_service, catalog_service, favorite_service,
gps_service, excursion_service e migration_UUID. Nenhum .env foi lido para testar.
Container encerrado após as suítes finais, com ID conferido; --rm removeu somente
esse banco descartável de teste. Docker Desktop permaneceu em execução.
Para repetir, recriar container local e schemas, aplicar migrações antes de usar
schemas antigos. Maven 3.9.14 em cache, Java 26 local com flag ByteBuddy;
projeto continua Java 21. Não usar banco/API de produção.

## Correção organizacional — escopo exclusivo de backend

Escrita restrita a C:\dev\bevans-backend. Não alterar C:\dev\bevans-frontend,
não implementar React Native/Expo/TypeScript aqui e não recriar staging de
frontend em target nem em outra pasta deste repositório. Histórico abaixo é
apenas registro anterior, não autorização para continuar trabalho de frontend.

Foram auditados e removidos 23 caminhos exclusivamente de frontend: diretórios
target/address-stage e target/next-frontend, 18 scripts .cjs de preparação/cópia,
os temporários address-document.md/editor-validation.md e o log frontend-editor.
Arquivos de staging correspondem ao frontend atual ou à versão commitada
bf2631f; não havia alterações únicas úteis de backend. Manifestos são snapshots
de preparação, não dependências. Nada foi copiado/movido para o frontend.
Nenhum desses artefatos estava versionado. Branch e commits preservados.

Antes de Maven clean, logs de backend (incluindo roteirizacao-testes.log
preexistente) foram preservados em .validation-logs/before-clean, ignorado pelo
Git. Código Java, testes, migrações e documentação válida não foram removidos.
Após a remoção, Maven clean verify passou e recompilou o backend do zero.
Log desta validação: .validation-logs/cleanup-clean-verify.log.

Sequência executada e próxima tarefa estão registradas no estado vigente acima.

## Histórico anterior após revalidação e editor urbano

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
