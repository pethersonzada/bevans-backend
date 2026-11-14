# Execução persistida de Excursões — incremento da #31

Endpoints privados com Firebase/MOTORISTA e titularidade no serviço:

- `POST /excursoes/{id}/execucoes?sentido=IDA&otimizar=false` inicia e retorna
  ID da execução, estado, instantes e snapshot do roteiro/participantes.
- `GET /excursoes/{id}/execucoes` reabre execuções persistidas, sem recalcular.
- `PUT /excursoes/{id}/execucoes/{execucaoId}/finalizacao` encerra.
- `PUT /excursoes/{id}/cancelamento` existente cancela também a execução ativa
  e inscrições, preservando snapshots/histórico.

Início sob lock da excursão captura apenas confirmados em snapshot interno
versionado (v1), armazenado em texto JSON privado. Não há token/contato no
snapshot e nenhuma URL pública o expõe. É cópia do roteiro da execução, não
histórico GPS. A origem/destino e ordem seguem `excursoes-roteiro-api.md`.
Repetir início de um sentido ativo retorna o mesmo ID/snapshot; otimizar=true
em um retry não recalcula. Existe no máximo uma execução ativa por excursão e
uma execução por sentido, garantidas também por constraint/índice PostgreSQL.

Após o primeiro início, nenhuma edição de coleta/capacidade/datas nem cadastro,
confirmação ou cancelamento individual é aceita, mesmo se o motorista iniciou
antes do horário previsto. Convites ficam indisponíveis para novos acessos.
Consulta individual por credencial continua sendo apenas da própria inscrição.
Prévia de um sentido já executado retorna 409: consultar a execução real.
Não há replanejamento ou reinício de sentido finalizado/cancelado nesta versão.

O motorista pode iniciar com atraso: início efetivo calcula sob lock e não
depende de a prévia anterior ao horário ainda estar disponível. A ação é
explícita do organizador, não automática pelo relógio/GPS. Não há janela mínima
antes da partida; essa política não foi inventada no backend.

Estados da execução: EM_ANDAMENTO, FINALIZADA, CANCELADA. Finalização repetida é
idempotente; finalização de cancelada recebe 409. Sem retornoEm, finalizar IDA
marca Excursao CONCLUIDA. Com retornoEm, ela permanece PUBLICADA entre sentidos,
mas continua congelada; VOLTA só inicia depois de IDA finalizada e exige origem
operacional configurada. Finalizar VOLTA marca Excursao CONCLUIDA. O estado de
publicação não substitui a consulta das execuções. Cancelada não reabre.

Migração incremental 012 após 011, sem alterar os scripts anteriores. JSON v1
é imutável na atualização normal da entidade; futura mudança do contrato de
snapshot exige migração/decoder compatível. Roteiro corrompido/versão desconhecida
retorna erro tratado 503. Não remover snapshots como rollback da aplicação.

Esta etapa não implementa embarque físico nem prova de desembarque: isso é o
domínio compartilhado #46. Finalização atual é ação explícita do motorista; não
verifica pessoas ainda na van até #46 existir. Não há rastreamento de Excursões
ou notificações automáticas nesta etapa. Reutilizar infraestrutura GPS com
autorização própria na integração futura, sem expor GPS por convite/credencial.
