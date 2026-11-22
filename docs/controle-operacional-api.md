# Controle operacional compartilhado — #46

Um domínio OperacaoViagem/RegistroOperacional/EventoOperacional atende
VIAGEM (privada ou pública) e EXCURSAO (ExecucaoExcursao), com identidade por
execução/sentido. Não usa presença/matrícula/inscrição como prova de embarque.

Novos inícios criam snapshot na mesma transação, por eventos internos síncronos:
privada usa presença aplicável + vínculo aprovado, endereço do sentido ou parada
escolhida; pública usa pedidos de embarque APROVADOS daquele dia/sentido e vínculo
aprovado; Excursão usa apenas inscritos confirmados do snapshot da execução.
Falha de snapshot desfaz o início inteiro. IDs repetidos não duplicam esperados.
Pins/ordem são internos; DTO de passageiro não contém pontos de terceiros.

API Firebase (tipo VIAGEM/EXCURSAO, ref é ID da Viagem/ExecucaoExcursao):
- GET `/operacoes/{tipo}/{ref}`: painel exclusivo do motorista, contadores e lista.
- PUT `/operacoes/{tipo}/{ref}/passageiros/{registro}/estado`: motorista próprio.
- GET no mesmo prefixo `/passageiros/{registro}/historico`: auditoria do motorista.
- GET `/operacoes/VIAGEM/{ref}/minha`: estado exclusivo do passageiro aprovado.
- PUT no mesmo prefixo `/minha/estado`: passageiro confirma apenas por si.
- GET `/minha/historico`: somente seus eventos.

Excursionista sem Firebase usa X-Inscricao (não X-Convite): GET/PUT nos caminhos
`/publico/excursoes/operacao/{execucaoId}/minha` e `/minha/estado`, além de GET
`/minha/historico`. Credencial precisa estar válida e vinculada à inscrição
incluída naquela execução. Não há lista pública, GPS ou ações por ID de terceiro.

Body de alteração:
```json
{"estado":"EMBARCADO","versao":0,"correcao":false}
```
Estados: AGUARDANDO → EMBARCADO → DESEMBARCADO; AGUARDANDO → NAO_EMBARCOU.
Passageiro OU motorista confirma, sem dupla aprovação. Mesmo estado é idempotente,
inclusive confirmação posterior pelo outro ator; não troca a primeira autoria
nem cria segundo evento. Mudança exige versão atual; um replay anterior à correção
não volta a embarcar pessoa corrigida. Desembarque sem embarque retorna 409.

Correção: somente motorista, `correcao:true`, versão atual e motivo obrigatório
(até 500 caracteres). Evento guarda antes/depois, autoria derivada do servidor,
origem, instante e versão. Não apaga eventos. Corrigir ausência/desembarque para
AGUARDANDO exige nova confirmação; correção para DESEMBARCADO exige estado
anterior EMBARCADO. Não há inferência por GPS.

Alterações só durante operação ativa. Retry do mesmo estado apenas retorna o
registro, mesmo após finalizar, sem nova escrita. Novas alterações em encerrada
retornam 409. Motorista atende quem não tem celular. Finalização/cancelamento de
Excursão e finalização/cancelamento de Viagem recusam pessoas ainda EMBARCADAS; motorista pode
registrar desembarque real ou correção auditada antes de encerrar.

Contadores vêm de registros persistidos: esperados = aguardando + naVan +
naoEmbarcaram + desembarcados. NaVan exclui desembarcados. Reconsultas em novas
transações não dependem de memória/SSE. O endpoint legado de rota usa o snapshot
durante viagem, sem recalcular silenciosamente residências/ordem em andamento.

Não há backfill automático de viagens antigas. POST autenticado do dono
`/operacoes/{tipo}/{ref}/preparacao` permite preparação explícita de fonte ativa
legada com dados atuais; não reconstrói fatos históricos. Operação já preparada
não é sobrescrita. Sem preparo, consulta retorna 404.

Migração 014 após 013, FKs/XOR de origem, unicidade por fonte/pessoa e evento por
versão. Locks seguem turma/excursão, os mesmos de início/encerramento e planos.
Eventos internos pós-alteração têm chave determinística para outbox da #47;
não fazem envio externo na transação. Não foram usados dados de produção.

## Descoberta de execuções da própria inscrição (Web)

`GET /publico/excursoes/inscricao/execucoes` com `X-Inscricao: <credencial individual>`.

- Retorna somente execuções que contenham um registro operacional criado para **a própria inscrição confirmada**. A lista pode estar vazia se a viagem ainda não começou.
- Cada item contém apenas `id` (execução), `sentido`, `estado` e `iniciadaEm`. Não devolve participantes, localização, paradas nem roteiro.
- Credencial inválida, expirada, inscrição não confirmada, cancelada ou excursão cancelada produzem HTTP 404, sem informar se outra inscrição existe.
- Resposta com `Cache-Control: no-store`, `Referrer-Policy: no-referrer` e `X-Robots-Tag: noindex, nofollow, noarchive`.
- Após obter um ID autorizado, o participante pode usar `GET /publico/excursoes/operacao/{id}/minha` e `GET .../historico`, sempre reenviando `X-Inscricao`. A autorização é verificada novamente em cada chamada.
- A credencial não deve ser persistida em URL, cookie, armazenamento local, logs ou monitoramento.

A execução precisa ter produzido seu registro operacional para ser descoberta. Nenhum identificador de execução é enumerado ou inferido.

**Validação de release:** testar em ambiente isolado com duas inscrições, data e sentido distintos, revogação/expiração e resposta a token alheio. Testes unitários ou HTTP simulados não substituem essa homologação.
