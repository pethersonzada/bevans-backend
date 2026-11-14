# Central de notificações

Incremento da issue #47: infraestrutura única para turmas privadas, rotas públicas e excursões. Migração incremental `015-notificacoes.sql`, posterior às migrações 001–014. Executar primeiro em cópia isolada; o Hibernate permanece em `validate`.

## Usuário Firebase

Todas as rotas abaixo exigem `Authorization: Bearer <ID token Firebase>`. O destinatário vem exclusivamente da sessão.

| Método | Rota | Contrato |
|---|---|---|
| GET | `/notificacoes?pagina=0&tamanho=20` | Página de avisos próprios, mais recentes primeiro; tamanho 1–50 |
| GET | `/notificacoes/nao-lidas` | Quantidade própria não lida |
| PUT | `/notificacoes/{id}/leitura` | Leitura idempotente; aviso alheio retorna 404 |
| PUT | `/notificacoes/leitura` | Marca somente os avisos próprios |
| GET/PUT | `/notificacoes/preferencias` | `pushOperacional`, `pushInformativo`; padrão `true` |
| POST | `/notificacoes/dispositivos` | `instalacaoId`, `token`, `consentePush: true` |
| DELETE | `/notificacoes/dispositivos/{instalacaoId}` | Revoga o dispositivo próprio no logout |

O registro também atualiza o token da mesma instalação. Identificador de instalação: 16–128 caracteres alfanuméricos, `_` ou `-`. Cada instalação e token são únicos. Vínculos de outro usuário retornam conflito, inclusive após revogação: não há transferência implícita entre contas. O aplicativo deve criar um novo identificador de instalação ao trocar de conta e obter token próprio. A resposta contém apenas ID, instalação, estado e atualização, nunca o token.

Preferências afetam push; não removem o histórico da central. Neste incremento todos os eventos implementados são operacionais; a preferência informativa fica reservada para eventos dessa categoria.

## Participante de excursão

`GET /publico/excursoes/inscricao/notificacoes?pagina=0&tamanho=20` e `PUT /publico/excursoes/inscricao/notificacoes/{id}/leitura` exigem a credencial individual no header `X-Inscricao`, com validade verificada. Convite `X-Convite` não autoriza a consulta. Acesso é limitado à inscrição vinculada à credencial; credencial inválida ou expirada retorna 404. Não se registra dispositivo Firebase de participante anônimo.

## Eventos e entrega

O evento operacional grava uma outbox na mesma transação do domínio. O consumidor só lê dados commitados. Consumo e criação da central são atômicos; uma chave única por evento/destinatário evita duplicação. A criação das entregas push ocorre junto com a central, uma entrega por dispositivo.

Ativar explicitamente o consumidor com `app.notifications.worker.enabled=true`; ativar FCM separadamente com `app.push.enabled=true`, usando a configuração Firebase Admin existente. Ambos ficam desativados por padrão. Nenhuma credencial é adicionada por este recurso. O consumidor processa no máximo dez eventos e dez entregas a cada cinco segundos. Reservas vencem em 120 segundos; falhas transitórias usam espera crescente e limite de cinco tentativas. FCM desativado/indisponível adia uma hora sem consumir tentativas reais. Tokens `UNREGISTERED` são desativados. Eventos e entregas em `FALHA` ficam preservados para diagnóstico e eventual recuperação administrativa, sem endpoint público de reenvio.

Push usa somente mensagem genérica e `notificacaoId`: sem nomes, coordenadas, endereços ou credenciais. Vínculo e preferência são revalidados antes de reservar a entrega. Revogação não cancela um envio externo que já esteja em andamento. A entrega externa tem semântica de pelo menos uma tentativa: uma falha após o FCM aceitar o envio pode gerar repetição; o cliente deve deduplicar por `notificacaoId`. Nunca marcar como entregue sem a confirmação do adaptador.

Implementados: ciclo de viagem/execução, embarque/desembarque/correções, aprovação/recusa de vaga e de embarque público, nova solicitação ao motorista, nova inscrição, confirmação e cancelamento de inscrição em excursão. Mudanças efetivas de turno/percurso de turma, configuração de coleta privada e horários/paradas de rota pública geram avisos aos vínculos aprovados. Repetir configuração sem mudança não cria aviso. O consumidor e o envio revalidam destinatário/contexto; desligados não recebem novos avisos da configuração antiga.

Excursões com inscrições reservadas/confirmadas já bloqueiam alterações de horários/percurso/coleta: essas regras foram preservadas, sem inventar alterações inseguras para produzir notificação. Cancelamento antes da partida avisa inscrições ativas por credencial; execução já iniciada usa o ciclo operacional comum, sem duplicar avisos de cancelamento dos confirmados.

Os [alertas de proximidade e resumos](proximidade-api.md) usam a mesma outbox e central. A issue #47 continua aberta para aceite externo Android/iOS e políticas de consentimento, não por ausência desses contratos locais.

### Contextos para navegação

`VIAGEM` e `EXECUCAO_EXCURSAO` dos eventos operacionais usam o ID da viagem/execução. Eventos anteriores à execução usam `TURMA` (turma), `ROTA_PUBLICA` (publicação), `SOLICITACAO_VAGA` (vínculo), `EMBARQUE` (solicitação) ou `EXCURSAO` (excursão). Isso distingue cancelamento antes da partida e cancelamento de execução sem colisão de IDs. O domínio operacional continua usando `tipo=EXCURSAO` em `/operacoes/EXCURSAO/{execucaoId}`; `contextoTipo` da central é a chave de navegação. O servidor valida novamente o recurso de destino ao consultá-lo.

### Cancelamento seguro de viagem

`PUT /viagens/{viagemId}/cancelamento`, com Firebase do motorista responsável, cancela turmas privadas e viagens de rotas públicas. Retorno 200; repetição é idempotente. Outra pessoa recebe 403, inexistente 404 e finalizada 409. Pessoas EMBARCADAS impedem o cancelamento: confirmar desembarque real ou correção auditada antes de encerrar. O mesmo lock de turma protege GPS/estado; posição é limpa e o evento `VIAGEM_CANCELADA` entra na outbox na mesma transação. Nova posição recebe 409. Para excursão, preservar `PUT /excursoes/{id}/cancelamento`.

## Recuperação

Fazer backup antes da migração. O rollback da aplicação deve manter as tabelas novas, outbox e histórico intactos. Desativar os dois consumidores antes de voltar a uma versão anterior; não apagar tabelas ou registros para desfazer esta entrega. Tokens persistidos são segredos operacionais e exigem a mesma proteção e controle de backup do banco. SQL de parâmetros bind não deve ser habilitado em produção.

FCM real e recebimento em dispositivos não foram testados. Os testes usam o adaptador desativado e resultados simulados para validar as transições locais, sem credenciais reais.
