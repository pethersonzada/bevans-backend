# Estado de retomada — 14/11/2026

## Estado vigente — coleta privada, controle operacional, notificações e GPS

Continuado o pedido anexado a partir de `e3665c0`, na **main** limpa. Executados
status/branch/log/fetch; comparação inicial: seis commits à frente e zero atrás
de `origin/main` (`415152e`). Não foi necessário pull. Os seis commits anteriores
`e1b5796`, `6518f0a`, `3eded6f`, `45f0281`, `26740d0`, `e3665c0` foram preservados.
Sem rebase/reset/clean, troca de branch, push, deploy ou acesso à produção.
Escopo de escrita limitado ao backend; nenhum frontend criado ou alterado.

### Commits desta execução

| Commit | Incremento |
|---|---|
| `bf4e1b9` | Coleta privada por turma/sentido, modos e paradas, sugestão Haversine e escolha persistida (#48) |
| `bed0568` | Controle operacional comum, embarque/desembarque, correções e auditoria (#46) |
| `bf0a8e4` | Central, outbox, preferências, dispositivos e adaptador FCM (#47) |
| `04b4f8a` | GPS autorizado de execuções de excursão com regras e SSE compartilhados (#31/#41) |
| `9ad82df` | Proximidade com GPS recente, ordem operacional e resumos do motorista (#47/#41) |
| `41a3b8d` | Alterações/cancelamentos, revalidação de destinatários e recuperação/retries da fila (#47/#46) |

O commit final desta retomada contém apenas documentação/índice de contratos.
Após ele, main fica **13 commits à frente e zero atrás da referência origin/main
consultada nesta execução**, com árvore limpa. Nenhuma issue foi fechada: aceite
mobile/FCM e políticas externas continuam pendentes. Comentários de progresso
foram publicados diretamente nas issues correspondentes.

### Funcionalidades e contratos

- #48: `ColetaPrivada`, `ParadaPrivada`, `EscolhaParadaPrivada`; configuração só
  do motorista, escolha própria/aprovada, IDs isolados, IDA/VOLTA independentes,
  restrições de paradas utilizadas e planejamento/viagem ativa. O modo antigo
  permanece PORTA_A_PORTA por ausência de configuração, sem backfill automático.
  Rota em paradas usa a escolha efetiva, sem residência. Endereços anteriores
  não são apagados quando omitidos na confirmação. Contrato: `turmas-coleta-api.md`.
- #46: `OperacaoViagem`, `RegistroOperacional`, `EventoOperacional`; mesma API
  para turmas, públicas FIXA/FLEXÍVEL e excursões, sem criar AlunoTurma para
  excursionistas. Snapshot esperado criado atomicamente no início; contadores
  persistidos, transições idempotentes, versão para impedir replay e correções
  auditadas pelo motorista. Finalização/cancelamento recusam EMBARCADOS.
  Contrato: `controle-operacional-api.md`.
- #47: `Notificacao`, `OutboxNotificacao`, `EntregaPush`, preferências e
  dispositivos. Evento/outbox na transação original, consumo posterior com
  reserva/retry/deduplicação; notificações e entregas criadas atomicamente.
  Firebase Admin existente, sem novas credenciais/dependências. Tokens rotacionados
  não são revogados por retorno tardio do token anterior. Preferência desativada
  conserva o histórico. Feed Firebase isolado e feed de inscrição por X-Inscricao.
  Contrato: `notificacoes-api.md`.
- Proximidade: amostra nova válida, precisão <=50 m, TTL 30 s, próxima ordem
  AGUARDANDO autorizada. Raios conservadores de 500/200/50 m, sem ETA, sem embarque
  automático, uma ocorrência por operação/registro/tipo. Revalida fila e push;
  GPS expirado, estado/ordem/faixa inválidos descartam o alerta. Resumos de
  aguardando/todos embarcaram ao motorista. Contrato: `proximidade-api.md`.
- GPS de excursão: `PosicaoExcursaoService`, `PosicaoExecucaoExcursao`,
  `RegrasGps`/`AmostraGps` compartilhados com viagens; mesmo PosicaoStreamService,
  limites de canais/expiração/revalidação. Apenas organizador envia; credencial
  individual válida + inscrição confirmada no snapshot consulta. Convite público
  não autoriza GPS. Encerramento limpa posição sob lock da excursão.
  Contrato: `excursoes-gps-api.md`.
- Cancelamento privado/público: `PUT /viagens/{id}/cancelamento`, Firebase do
  motorista, idempotência, bloqueio de pessoas embarcadas e limpeza GPS atômica.
  Alterações efetivas de horário/percurso geram avisos próprios; salvar sem mudar
  não gera novos avisos. Excursões preservam o bloqueio de alterações com reservas
  e inscrições ativas. Notificações usam contexto `EXECUCAO_EXCURSAO` para execução
  e `EXCURSAO` para excursão, evitando colisão de IDs antes/depois da partida.

### Migrações e validação real

Scripts **001–012 intactos**. Novos scripts incrementais:

1. `013-coleta-privada.sql`: modos, paradas e escolhas, sem modificar turmas antigas.
2. `014-controle-operacional.sql`: origem exclusiva, registros e histórico versionado.
3. `015-notificacoes.sql`: central, outbox, dispositivos, preferências e entregas.
4. `016-gps-excursoes.sql`: última amostra por execução, FK e constraints.

Preservado `Hibernate validate` como padrão da aplicação. `UpgradeMainSchemaPostgresIT`
aplica fixture da main com dados e todos os 16 scripts; `OperacaoPostgresIT` também
cria schema aleatório migrado antes do contexto e usa validate. Os antigos fixtures
de serviços isolados foram preservados. Nada executado em produção. Recuperação:
desativar recursos novos e preservar tabelas, IDs, snapshots, outbox/histórico;
nunca usar DROP/recriação como rollback. Ver planos nos respectivos contratos.

| Verificação | Resultado |
|---|---|
| Baseline reexecutada desta sessão | 246 testes padrão aprovados |
| Após #48 | 255 padrão / 86 PostgreSQL aprovados |
| Após #46 | 265 padrão / 94 PostgreSQL aprovados |
| Central inicial | 269 padrão / 96 PostgreSQL aprovados |
| GPS de excursão | 275 padrão / 97 PostgreSQL aprovados |
| Proximidade | 276 padrão / 98 PostgreSQL aprovados |
| **Final no JDK 21.0.12.1** | **282 padrão / 103 PostgreSQL aprovados**, zero falhas/erros/ignorados |

Comparação com o estado anterior: +36 testes padrão e +25 execuções PostgreSQL
sobre 246/78. Nenhum teste anterior removido/desabilitado. Cobertura inclui
autorizações/isolamento, concorrência, persistência, transições/correções, sentidos,
OR-Tools, GPS/TTL, migrações, leitura, tokens, preferências, outbox após rollback,
reserva vencida, dois consumidores, cinco tentativas e adaptador FCM indisponível.
Reconsulta em novas transações foi testada; não afirmar reinício real de dispositivo.

Logs locais ignorados:

- `.validation-logs/restantes-baseline.log`;
- `.validation-logs/coleta-privada-clean-verify.log`, `coleta-privada-postgres.log`;
- `.validation-logs/operacao-clean-verify.log`, `operacao-postgres.log`;
- `.validation-logs/notificacoes-clean-verify.log`, `notificacoes-postgres.log`;
- `.validation-logs/gps-excursoes-clean-verify.log`, `gps-excursoes-postgres.log`;
- `.validation-logs/proximidade-clean-verify.log`, `proximidade-postgres.log`;
- **`.validation-logs/final-java21-clean-verify.log`**;
- **`.validation-logs/final-java21-postgres-reexecutado.log`**.

As primeiras suítes usaram JDK 26 com opção ByteBuddy experimental; os resultados
finais acima foram repetidos no **JDK 21 instalado**, sem essa opção. Usado Maven
3.9.14 do cache do wrapper com `-o` e repositório local Maven do usuário.

Comandos finais, após preparar os schemas locais conforme a CI:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1'
$mavenLocal=rg --files "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.14" -g mvn.cmd | Select-Object -First 1
& $mavenLocal -o "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" clean verify
& $mavenLocal -o "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" -Ppostgres-validation verify
```

Houve falha de infraestrutura durante a primeira execução PG no JDK 21: Docker
Desktop deixou de responder e a conexão local foi recusada. Log preservado em
`.validation-logs/final-java21-postgres.log` (100 erros de conexão, não aprovação).
Engine restabelecido, container descartável recriado, schemas preparados e suíte
inteira repetida com sucesso. Container final de teste:
`742d74a7ed90fbe239f14e0a41545c2d085e547fe54cd1065c2c1a908899b8c6`,
nome `bevans-restantes-validation`, PostgreSQL 16, bind apenas 127.0.0.1:55435.
Encerrado ao concluir, após conferir identidade; Docker Desktop mantido.

No perfil Maven postgres-validation, pools de teste limitados a quatro conexões,
idle mínimo um e cache de quatro contextos para não exaurir o PG com novos
contextos. Não foram alterados pools/configurações de produção. CI preservada:
push main, pull_request e workflow_dispatch, Java 21/PostgreSQL 16, schema privado
adicionado. CI remota não executada, pois não houve push.

`git diff --check` aprovado. BackendBoundaryTest aprovado, inclusive inspeção de
target. Nenhum frontend/JS/TS/Expo/Next, token real, credencial ou script auxiliar
temporário identificado nos arquivos versionados. Target contém só artefatos de
build Maven e permanece ignorado; logs não serão versionados.

### Pendências reais e próximo passo

Backend entregue localmente e testado; não declarar aceite global/produção.
O próximo trabalho exige integração externa que esta sessão não pode simular:

1. Revisar commits/contratos e autorizar eventual push. Nenhum envio foi feito.
2. Integrar o aplicativo no repositório correto, fora deste escopo de escrita;
   validar mapas, reconexão, permissões, logout e segundo plano em aparelhos.
3. Validar FCM Android/iOS em pelo menos dois aparelhos com ambiente/consentimento
   apropriados. **Não houve envio FCM real nem prova de recebimento.** Consumidor
   `app.notifications.worker.enabled` e envio `app.push.enabled` exigem ativação
   explícita e permanecem desativados por padrão.
4. Definir/validar política de menores e responsáveis, retenção e consentimento
   com o produto. Não presumir que Firebase ou aceite genérico resolve essa regra.
5. Validar end-to-end os critérios remanescentes de #48/#46/#47/#41/#31 e somente
   então considerar fechamento. Participantes sem aplicativo têm feed individual;
   canal externo adicional exige consentimento e infraestrutura próprios.

Não retomar a auditoria do zero nem refazer as seis entregas anteriores de Excursões.
Para repetir PG, seguir CI/contratos e recriar apenas ambiente descartável local.

---

## Histórico da execução anterior — 13/11/2026

## Estado vigente — implementação na main em 13/11/2026

Pedido anexado de evolução backend recebido e lido. Escopo de escrita continua
somente C:\dev\bevans-backend; nenhum frontend/JS/Expo/Next criado ou alterado.
Branch inicial **main**, árvore limpa e origin/main sincronizada em **415152e**.
Executados status/branch/fetch e pull --ff-only (já atualizada). Trabalho anterior
preservado; nenhuma branch criada/excluída, rebase, reset, push ou deploy.
Commits novos seguem português natural, sem Conventional Commits.

### Auditoria e baseline desta execução

Consultadas diretamente issues #28–#32, #35, #39–#41, #46–#48 e comentários.
Comparados contratos de Excursões, turmas, rotas/embarques, presença, endereços,
GPS/SSE, políticas Firebase/Spring, OR-Tools, Maven/CI e migrações existentes.
Baseline da main atual: **209 testes padrão aprovados**, zero falhas/erros/ignorados,
log .validation-logs/main-pendencias-baseline.log. Não havia falha inicial reproduzida.
Main agora usa Hibernate **validate por padrão**, SQL logging/open-in-view
desabilitados e JSON rejeita capacidade fracionária; preservar esses commits.
Não confundir com histórico abaixo que ainda descreve ddl-auto=update.

### Commits locais e funcionalidades verificadas

- **e1b5796** — Implementando modos de coleta e paradas em excursões:
  PORTA_A_PORTA/PARADA_UNICA/MULTIPLAS_PARADAS, paradas próprias, pins confirmados,
  ordenação, versões, remoção lógica e isolamento. Migração **009**.
  Verificado: 215 testes padrão + 57 execuções PostgreSQL.
- **6518f0a** — Adicionando convites seguros para excursões:
  SecureRandom 256 bits, hash SHA-256, expiração/revogação, metadados privados e
  consulta pública limitada por X-Convite. Rate limit, no-store e sem GPS/PII.
  Migração **010**. Verificado: 224 padrão + 61 execuções PostgreSQL.
- **3eded6f** — Implementando inscrições e controle de vagas em excursões:
  credencial individual independente e hashada, reservas de 15min, confirmação,
  cancelamento/expiração, duplicidade, capacidade concorrente e bloqueios de
  mudanças incompatíveis. Migração **011**. 234 padrão + 70 PostgreSQL.
- **45f0281** — Integrando roteiros de excursões ao OR-Tools:
  apenas confirmados, três modos, ordem fixa por padrão, otimização explícita,
  origem/destino e ida/volta, sem mutação de cadastro. 239 padrão + 73 PostgreSQL.
- **26740d0** — Implementando execução persistida e encerramento de excursões:
  snapshot privado versionado, identidade por sentido, início idempotente e
  concorrente, finalização/cancelamento e congelamento pós-início. Migração **012**.
  Último estado verificado: **246 padrão + 78 execuções PostgreSQL**.

Todas as suítes finais de cada incremento passaram, sem falhas/erros/ignorados.
PostgreSQL repete regras H2 e acrescenta migrações: não somar como casos únicos.
Nenhum teste anterior removido; os 37 casos padrão novos ampliam a baseline.
Erro intermediário de compilação no novo teste (var em declaração composta)
foi corrigido antes do commit; nenhuma falha foi mascarada/desabilitada.

### Contratos novos e arquivos principais

Código organizado em models/repositories/DTOs/controllers/services de Excursões:
ParadaExcursao, ConviteExcursao, CredencialInscricaoExcursao, InscricaoExcursao,
ExecucaoExcursao; ExcursaoService/ConviteExcursaoService/InscricaoExcursaoService/
RoteiroExcursaoService/ExecucaoExcursaoService. SecurityConfig libera somente
métodos/caminhos públicos específicos; LimiteConvitesFilter protege o prefixo.
URLs/credenciais de teste são explícitas; não foi consultado .env para obtê-las.
Não houve integração com Supabase ou alteração de credenciais Firebase.

Contratos detalhados para consumidores, sem implementação frontend:
- excursoes-coleta-api.md: PUT /excursoes/{id}/coleta e GET de detalhes.
- excursoes-convites-api.md: administração de convites + GET público por header.
- excursoes-inscricoes-api.md: emissão de credencial, cadastro/consulta/cancelamento
  próprios, confirmação pelo dono, listagem e vagas. X-Inscricao é segredo próprio.
- excursoes-roteiro-api.md: POST planejamento privado, IDA/VOLTA e OR-Tools.
- excursoes-execucao-api.md: iniciar/listar/finalizar execuções e snapshot.

Antes de cadastro, o cliente deve guardar a credencial emitida, depois reenviá-la
em retries com os mesmos dados. Não usar token na URL da API, analytics ou logs.
Link compartilhável deve ser montado pelo consumidor com fragmento local.
Convite/credencial não autorizam administração, GPS ou dados de outros inscritos.
Consentimento simples de privacidade não é consentimento legal verificado de menor.

### Banco e validações

Scripts **001–008 preservados**, novos incrementos **009–012**. Upgrade completo
executa fixture dos seis modelos da main com dados/IDs/FKs e todas as migrações;
Hibernate valida todos os mapeamentos sem completar DDL. Testes SQL verificam
unicidade parcial de inscrições, validade de coordenadas, uma execução ativa e
uma por sentido. CI atualizada para schemas invite_service/enrollment_service.

Banco exclusivo de teste: PostgreSQL 16, container bevans-pendencias-validation,
ID **6692466b0a720e4668077782eb889382422e1e56fedce78b6e9a1ef3cd064042**,
bind 127.0.0.1:55435, bevans_validation, credenciais somente de teste.
Schemas capacity_service/publication_service/boarding_service/catalog_service/
favorite_service/gps_service/excursion_service/invite_service/enrollment_service
e migration_UUID/upgrade_UUID. Docker Desktop precisou ser iniciado em segundo plano.
Container encerrado ao fechar esta sessão após conferir ID; --rm removeu somente
esse banco de teste. Docker Desktop permaneceu em execução.

Logs ignorados em .validation-logs: main-pendencias-baseline.log e os pares
*-clean-verify.log/*-postgres.log para excursao-coleta, convites, inscricoes,
excursao-roteiro e excursao-execucao. Testes direcionados também registrados.
Maven 3.9.14 em cache, Java 26 local com flag ByteBuddy experimental; projeto e
CI continuam Java 21. Não foi executada CI remota nem teste em dispositivos.
Reabertura em transações distintas foi testada; reinício literal de VM/app não.

### Pendências exatas e próxima implementação

Não declarar todo o pedido concluído. #29/#30/#31 avançaram em backend; issues
receberam comentários reais e continuam abertas para aceite completo/integração.
Não foram fechadas issues de frontend ou alterados consumidores externos.

Próxima tarefa de backend: **#48**, modo privado por turma/sentido, paradas próprias,
escolha persistida separada da sugestão geográfica, endereço só do titular,
congelamento durante viagem e preservação porta a porta IDA/VOLTA. Manter essas
paradas separadas de ParadaExcursao/ParadaPublica. Roteiro manual por padrão.
Depois **#46**: um domínio operacional comum aos três módulos, com snapshot de
esperados, aluno OU motorista confirma, desembarque/ausência/correções auditáveis
e contadores. Não confundir inscrição CONFIRMADA com EMBARCADO. Excursões já têm
execucaoId persistido para essa integração; não duplicar o cadastro escolar.
Finalização atual de Excursões é explícita do motorista e ainda não verifica
pessoas embarcadas porque o controle físico compartilhado ainda não existe.

**#47** segue pendente: central persistente única, preferências, outbox/deduplicação,
dispositivos/revogação/FCM e alertas por ponto realmente escolhido, ordem real e
GPS recente. Não enviar push real de teste ou simular FCM para participante sem
dispositivo. GPS de Excursões precisa de adaptação autorizada por inscrição e
execução, reaproveitando infraestrutura existente, sem herdar autorização escolar
ou criar canal público. #41 escolar/SSE permanece preservada.

**#32**: fluxo API real básico já testado com H2, mas demo integrada mobile/web,
dois dispositivos, FCM real, consentimento verificado de menores, retomada após
reinício literal e validação SSE/Nginx real continuam externos/pendentes.
Não há impedimento geral registrado para continuar o backend restante; não
tratar essas limitações externas como conclusão de #48/#46/#47. Retomar daqui,
sem repetir auditoria completa ou reimplementar os cinco incrementos acima.

## Histórico — PR #45 e integração local anterior

Main integrada por merge normal após fetch, de `9ef969d` até origin/main `1a24bb3`.
Conflitos de RotaController, ViagemService e teste de contexto resolvidos
combinando contratos/segurança/concorrência da main com os módulos da branch.
Workflow backend-tests preservado e ampliado para clean verify + PostgreSQL;
gatilhos push/main, pull_request e workflow_dispatch. Execução CI remota depende
do push manual; nenhum push, deploy ou merge do PR foi realizado.

Verificações desta integração: **209 testes em clean verify**, **52 execuções
PostgreSQL**, zero falhas/erros/ignorados. Novo teste de upgrade reproduz schema
dos seis modelos da main com dados, aplica índice de viagem ativa + 001–008 e
valida mapeamentos Hibernate sem update automático. Nenhum teste removido.
Revisão detalhada, limitações de ddl-auto=update e logs: `pr45-merge-review.md`.
Branch pronta localmente para push manual; aguardar Actions antes do merge remoto.
Próxima funcionalidade continua #30; não reiniciar auditoria ou implementar frontend.

## Histórico — backend apenas, após os novos lotes

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
