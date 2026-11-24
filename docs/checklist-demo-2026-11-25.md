# Ensaio do BeVans para 25/11/2026

Usar duas contas descartáveis do mesmo projeto Firebase de homologação, com os perfis MOTORISTA e PASSAGEIRO já vinculados aos respectivos UIDs no banco. Abrir cada conta em um perfil de navegador separado. Registrar o SHA do backend, o SHA do webapp e a origem exata do Preview ensaiado. O contrato conferido foi o do [webapp no PR #66, revisão cd9851b](https://github.com/pethersonzada/bevans-webapp/pull/66).

## Antes de abrir o painel

| Componente | Conferir na configuração do ambiente |
| --- | --- |
| PostgreSQL | `DATABASE_URL` no formato JDBC, `DATABASE_USERNAME`, `DATABASE_PASSWORD`. Banco exclusivo de homologação, migrações até 023 e privilégios da aplicação conferidos. Usar `HIBERNATE_DDL_AUTO=validate`. |
| Firebase Admin | `APP_FIREBASE_ENABLED=true` e `APP_FIREBASE_CREDENTIALS_JSON` privada ou `APP_FIREBASE_CREDENTIALS_PATH` apontando para arquivo protegido. Confirmar acesso ao serviço de autenticação e aos certificados públicos do Google. |
| API HTTPS | `PORT` quando fornecida pelo provedor e `APP_CORS_ALLOWED_ORIGINS` com as origens exatas autorizadas para aquele ambiente, separadas por vírgula. |
| Webapp na Vercel | `NEXT_PUBLIC_BEVANS_API_URL` e `NEXT_PUBLIC_BEVANS_FIREBASE_API_KEY` de homologação no **build de Preview**. Conferir o domínio autorizado no Firebase. `NEXT_PUBLIC_BEVANS_MAPBOX_TOKEN` é necessário se houver mapa no roteiro. |
| Recursos opcionais | `MOTORISTA_CADASTRO_CODIGO` para cadastro de novo motorista. Para demonstrar a chegada de notificações novas, conferir o processamento da fila no ambiente de ensaio. O histórico pode estar vazio quando o worker está desligado. |

As opções de isolamento dos workers e as permissões do banco estão no [guia de homologação](homologacao-railway.md).

## Disponibilidade e preflight

Preencher as duas variáveis do PowerShell com os endereços de homologação que serão usados no navegador. No PR #66, o Preview registrado foi `https://bevans-webapp-e0kmgkzry-pethersonzadas-projects.vercel.app`. Um novo deploy pode gerar outra origem.

```powershell
$api = 'https://HOST-DA-API-DE-HOMOLOGACAO'
$origem = 'https://HOST-DO-PREVIEW-DO-ENSAIO'
curl.exe -i "$api/health"
curl.exe -i -X OPTIONS "$api/turmas" -H "Origin: $origem" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: authorization,content-type"
curl.exe -i "$api/notificacoes" -H "Origin: $origem"
```

- [ ] `/health` responde 200 com `OK`. Abrir o painel com cada conta para verificar também Firebase e acesso ao perfil no PostgreSQL.
- [ ] O preflight responde 200 com `Access-Control-Allow-Origin` igual a `$origem`, método POST e cabeçalhos `authorization` e `content-type`.
- [ ] Notificações sem token retornam 401 com o cabeçalho CORS da origem autorizada. No navegador autenticado, confirmar 200 na consulta do próprio perfil por UID. Evitar capturas de tela que mostrem o cabeçalho Authorization.

## Roteiro por perfil

| Passo | Ação em homologação | Confirmação na API |
| --- | --- | --- |
| 1. Motorista | Entrar, atualizar a página, criar uma turma com nome, turno e assentos. Guardar o convite dentro do ensaio. | `GET /usuarios/por-uid/{uid}` devolve o UID e o perfil corretos. `POST /turmas` com `motorista.id` e `capacidade` devolve `id` e `codigoConvite`. A turma aparece em `GET /turmas/motorista/{id}`. |
| 2. Passageiro | Solicitar entrada por código. Conferir o pedido com o motorista. | `POST /turmas/entrar/{id}?codigo=…` retorna 200. O vínculo fica pendente, a lista de turmas aprovadas continua vazia para uma conta nova. Um pedido repetido retorna 409. |
| 3. Motorista | Abrir pendências e aprovar. Ensaiar a recusa com outro pedido pendente. | `GET /turmas/{id}/pendentes` traz o ID do vínculo e `aluno.nome`. `PUT /turmas/analisar/{vinculoId}?status=APROVADO` ou `REJEITADO` confirma a decisão. Conferir o aprovado em `GET /turmas/{id}/passageiros` e, com ele, em `GET /turmas/usuario/{id}/todas`. |
| 4. Rotas | Motorista abre suas rotas, confere paradas e publicação. Passageiro pesquisa a rota publicada por cidade ou destino. | `GET /rotas-publicas/minhas` retorna lista. `GET /rotas-publicas/catalogo` retorna `itens`, `pagina`, `total` e `totalPaginas`. Conferir o ID ao abrir o detalhe. |
| 5. Embarque | Passageiro escolhe data de operação, sentido e parada ativa. Motorista consulta a mesma data e sentido e analisa o pedido. | `POST /rotas-publicas/{id}/embarques` recebe `dataOperacao`, `sentido` e `paradaId`. Conferir `id`, `rotaId` e `status` em `GET /embarques/minhas` antes e depois de `PUT /embarques/{id}/analise`. |
| 6. Viagem e operação | Preparar uma viagem de teste com passageiro esperado. Motorista abre o painel e passageiro consulta seu estado. | `GET /rota/status-atual?turmaId=…` traz `ATIVA` e `viagemId` quando há viagem ativa. Consultar `GET /operacoes/VIAGEM/{viagemId}` como motorista e o sufixo `/minha` como participante. Uma turma em rascunho pode aparecer `INATIVA`. |
| 7. Excursões e notificações | Organizador abre a excursão e suas execuções. Para o passageiro, ensaiar com convite e inscrição próprios. Abrir o histórico e marcar um aviso existente como lido. | `GET /excursoes` traz `itens`. `GET /excursoes/{id}/execucoes` traz lista. A API oferece `GET /passageiro/excursoes` para inscrições vinculadas. A aba de passageiro do PR #66 orienta o uso do convite individual. `GET /notificacoes` traz `content` e `GET /notificacoes/nao-lidas` traz um número. |

## Evidências e pendências do ensaio

- Em 23/11, Java 21.0.12.1 e Maven Wrapper 3.9.14 executaram `clean verify` com **398 testes** e geração do JAR. A suíte usa H2, MockMvc e autenticação simulada. Uma regressão cobre a troca do convite enquanto o ingresso aguarda o bloqueio da turma. Nesse caso, o passageiro deve usar o novo código.
- O perfil `-Ppostgres-validation verify` executou **144 testes** em PostgreSQL 16.4 descartável. O teste `TurmaPainelPostgresIT` executou criação, ingresso, conflito de duplicidade, aprovação e consulta pelos dois perfis no schema migrado com Hibernate em `validate`. As duas suítes terminaram sem falhas, erros ou testes ignorados.
- Os sete testes Python do gerador de evidências do CI passaram com `py -3 -m unittest discover -s scripts/ci -p test_gerar_evidencias.py`.
- Na API publicada configurada no webapp, a consulta de saúde retornou `OK`. O preflight de `POST /turmas` respondeu 200 para `https://bevans-webapp.vercel.app`, com `authorization, content-type`. A consulta anônima de notificações retornou 401. Foram feitas apenas leituras e preflight.
- [ ] Validar no Preview escolhido o login real, F5, troca de conta, CORS da API de homologação e as gravações do roteiro. GPS, mapas e entrega de push dependem de serviços ou dispositivos externos. Registrar quais foram ensaiados antes de incluí-los na apresentação.

## Contingência durante a apresentação

- **Falha de login ou de perfil.** Em 401, aguardar a renovação pelo SDK e entrar novamente se necessário. Em 503, conferir disponibilidade do Firebase e tentar a consulta mais tarde. Em 403, conferir conta, papel e titularidade do recurso. Se faltar perfil para o UID, usar a conta de homologação previamente vinculada.
- **Falha de CORS ou rede.** Conferir o endereço da barra do navegador, a API do build e a resposta ao preflight acima. Se o Preview estiver indisponível, usar o ambiente de ensaio previamente validado ou uma gravação identificada como tal.
- **Resposta perdida após uma gravação ou conflito 409.** Reabrir a lista de turmas, vínculos, embarques ou a operação e conferir o estado persistido antes de repetir a ação. Usar o ID devolvido e a versão atual nos fluxos de operação.
- **Banco, viagem ou mapa indisponível.** Se a API falhar na inicialização, conferir conectividade, versão do schema e privilégios com o responsável pelo ambiente. Para viagem inativa ou sem GPS, demonstrar o estado retornado e seguir para uma consulta disponível. Reservar correções de infraestrutura para depois do ensaio.
