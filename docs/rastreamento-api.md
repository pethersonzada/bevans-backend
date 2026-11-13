# Rastreamento privado de viagens — backend #41

O servidor mantém somente a última amostra por viagem em PostgreSQL. Não grava
histórico de deslocamentos nem publica GPS no catálogo. Vale para viagens de
turmas privadas ou de rotas publicadas; não cria vínculo com Excursões.

Obtenha `viagemId` em `GET /rota/status-atual?turmaId=...` depois de iniciar a
viagem. Os endpoints novos exigem Bearer Firebase no header. Serviço revalida
motorista responsável ou passageiro com vínculo APROVADO na turma da viagem.
Conhecer ID, convite, favorito ou publicação não autoriza rastreamento.

`PUT /viagens/{viagemId}/localizacao`, somente motorista responsável:

```json
{"amostraId":"uuid-do-dispositivo","latitude":-8.1,"longitude":-35.1,
 "observadoEm":"2026-11-13T12:00:00Z","precisao":8.0,"velocidade":10.0}
```

Precisão em metros; velocidade opcional em m/s. Intervalo mínimo 5 segundos,
amostra no máximo 30 segundos antiga ou 5 segundos adiantada. Timestamp deve
ser posterior ao início da viagem quando conhecido, e crescer a cada nova
amostra. Coordenadas finitas, precisão 0–5000 e velocidade 0–100. Mesmo ID/dados
é idempotente e não estende TTL; mesmo ID com dados diferentes retorna 409.
Datas normalizadas em microssegundos para persistência PostgreSQL. Retorno 429
exige esperar; descarte amostras antigas na reconexão em vez de reenviar uma fila.

`GET /viagens/{viagemId}/localizacao` retorna `ATUALIZADA`, `SEM_LOCALIZACAO`,
`DESATUALIZADA` ou `FINALIZADA`. Somente ATUALIZADA inclui coordenadas, precisão e
velocidade. TTL de 30 segundos pelo instante observado, inclusive no retry.
Respostas não devem ser cacheadas. Não extrapolar posição quando offline.

`GET /viagens/{viagemId}/localizacao/eventos` usa SSE com eventos `localizacao`,
reconexão sugerida 10 segundos, revalidação de perfil/vínculo e emissão a cada
10 segundos. No máximo 2 conexões por usuário e 200 globais nesta VM. Fecha ao
observar finalização, expiração do token, revogação do vínculo ou falha de rede.
Conexão dura no máximo 5 minutos; renove token antes de reconectar. Nunca envie
tokens na URL. Cliente deve suportar Authorization no SSE; se não suportar,
use polling a cada 10 segundos, com backoff ao desconectar e pausa em background.
`X-Accel-Buffering: no` evita buffering Nginx sem alterar infraestrutura.

Finalizar usa o mesmo lock de turma que o envio, limpa a última posição e
impede novas amostras. GET e próximo evento SSE retornam FINALIZADA sem GPS;
um evento que já estivesse em trânsito não pode ser recolhido. Uma nova viagem
tem outro ID; envios atrasados à anterior são recusados. O endpoint legado
`/rota/localizacao-van` retorna 410 com orientação de migração, depois da
autorização existente; não mantém um segundo canal em memória sem TTL.

Retenção: somente última amostra enquanto ativa, sobrescrita no envio e
limpa ao finalizar. Viagem esquecida ativa deve ser encerrada pelo motorista;
TTL impede divulgação de posição antiga. Não há histórico nem GPS em favoritos,
catálogo ou DTO de rota. Não habilitar rastreamento público de menores; vínculo
APROVADO não comprova consentimento do responsável. Fluxo de consentimento de
menores e cliente mobile/background continuam pendentes de implementação.

Aplicar incremental 007 depois de 001–006 em ambiente isolado. Coluna de início
legada fica nula, preservando dados; não inferir timezone de datas históricas.
Rollback de aplicação preserva tabela/IDs. Nenhum script toca produção.

Testes H2/PostgreSQL: persistência, autorização, revogação, TTL, retry, ordem,
rate limit, duas publicações concorrentes e corrida entre envio/finalização.
Testes SSE: limites, expiração, revogação e reconexão. HTTP usa Firebase simulado.
Rede real/dispositivo Expo/permissões/background não validados neste backend.
