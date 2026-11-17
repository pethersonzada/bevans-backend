# Roteiro completo de uma viagem

`GET /operacoes/VIAGEM/{viagemId}/roteiro`

Autenticação Firebase obrigatória; somente o motorista proprietário da operação pode consultar. Resposta com `Cache-Control: no-store`, inclusive para viagens finalizadas. O identificador é da viagem, não da turma nem da operação.

Retorna uma lista ordenada de pontos com `tipo`, `paradaId` (nullable), `nome`, `latitude`, `longitude` e `passageiros` (`id`, `nome`, `latitude`, `longitude`). Os tipos são `ORIGEM`, `DESTINO`, `PARADA`, `INDIVIDUAL` e `EXCEPCIONAL`. Vários passageiros podem compartilhar uma parada; paradas sem passageiros continuam na lista. IDs de passageiros e paradas pertencem a domínios diferentes.

O roteiro é salvo na mesma transação da partida. Falha de planejamento ou de persistência desfaz viagem, operação, registros e eventos. A consulta não recalcula a rota nem usa o cadastro atual. Cada nova viagem no mesmo dia recebe um snapshot próprio.

- Privada porta a porta: endereço próprio da presença autorizada para IDA/VOLTA; ausência de ponto válido gera 409 com `codigo=PONTO_INVALIDO`, sem omissão parcial.
- Privada com paradas: extremos da turma, todas as paradas ativas em ordem do sentido e passageiros confirmados nas paradas escolhidas.
- Pública: extremos da publicação pública, todas as paradas ativas e pontos excepcionais aprovados. FIXA preserva sequência cadastrada (invertida na VOLTA); exceções aprovadas entram após as paradas e antes do destino. FLEXIVEL otimiza os pontos intermediários na partida, preservando extremos. Somente pedidos aprovados da data/sentido com vínculo ainda aprovado entram como passageiros.
- Excursão: permanece usando os snapshots e endpoints próprios `/excursoes/{id}/planejamento` e `/excursoes/{id}/execucoes`.

Migração necessária: `021-roteiro-operacional.sql`. Operações anteriores sem snapshot completo retornam 409 explicando a indisponibilidade; não se reconstrói histórico com dados atuais. JSON salvo ilegível retorna 503. O endpoint legado `/rota/otimizar` mantém o formato de **passageiros**, usando os registros salvos quando há viagem ativa. Ele não substitui este roteiro completo; sentido diferente do ativo retorna 409 e operação ativa sem snapshot de passageiros retorna 503.

Compatibilidade: painel operacional e lista legada de passageiros mantêm seus formatos. O frontend #47 (`81e9d8e`) pode continuar conciliando passageiros por IDs/contadores; para navegar exclusivamente pelo snapshot completo, deverá adotar esta consulta. Este repositório não altera o frontend.

## Estado e localização

O endpoint de status existente preserva `status=ATIVA|INATIVA` e acrescenta `estado=NAO_INICIADA|EM_ANDAMENTO|FINALIZADA|CANCELADA`. `viagemId` identifica apenas a ativa; em estado inativo com histórico, `ultimaViagemId` identifica a última execução.

A consulta de GPS continua separada: `SEM_LOCALIZACAO`, `ATUALIZADA`, `DESATUALIZADA` ou `FINALIZADA`. Ausência ou expiração do GPS não encerra a viagem. Aos 30 segundos da observação a posição expira e suas coordenadas deixam de ser divulgadas. A segunda viagem começa sem reutilizar o GPS da anterior. Falhas HTTP de rede/autorização devem ser tratadas como falhas, sem serem convertidas em estado de viagem.

## Excursões e atraso

O horário cadastrado é previsto: não há prazo máximo de atraso para partida enquanto a excursão estiver PUBLICADA. Permanecem obrigatórios proprietário autorizado, roteiro consistente, unicidade por sentido, ausência de outra execução ativa e IDA finalizada antes da VOLTA. O retorno exige horário e origem operacional configurados. Não foi criada uma tolerância arbitrária que bloqueie viagens legítimas.

A prévia de leitura é permitida antes, no instante e após a partida prevista, sem alterar cadastro. Após iniciar, retorna o snapshot daquele sentido, inclusive depois de finalizar/cancelar. Sem snapshot, excursões canceladas/concluídas não ganham novo planejamento.

Conflitos acrescentam `codigo` sem remover `erro`, `status`, `timestamp` e `caminho`: `ESTADO_INVALIDO`, `EXECUCAO_EXISTENTE`, `EXECUCAO_ATIVA`, `IDA_NAO_FINALIZADA`, `ROTEIRO_INCONSISTENTE` e `PONTO_INVALIDO`. Os demais erros existentes preservam seu contrato.
