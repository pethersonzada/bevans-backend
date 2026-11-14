# GPS autorizado de execuções de excursão

Incremento de integração com #31/#41/#47. Migração `016-gps-excursoes.sql` após 015, sem alterações nos scripts anteriores. A única posição atual fica vinculada à execução de excursão, com FK; não há histórico GPS nem conversão em `Viagem`/`AlunoTurma`.

## Organizador

Firebase e titularidade são obrigatórios em:

- `PUT /execucoes-excursao/{execucaoId}/localizacao` — mesmo corpo `PosicaoRequest` de `/viagens/{id}/localizacao`: `amostraId`, `latitude`, `longitude`, `observadoEm`, `precisao`, `velocidade` opcional.
- `GET /execucoes-excursao/{execucaoId}/localizacao`.
- `GET /execucoes-excursao/{execucaoId}/localizacao/eventos` — SSE.

## Participante

`GET /publico/excursoes/operacao/{execucaoId}/localizacao` e a variante `/eventos` exigem `X-Inscricao`: credencial individual válida, da mesma excursão, inscrição confirmada e incluída no snapshot operacional daquela execução. Convite de divulgação não autoriza. Não existe envio público de posição. Credencial ausente, expirada, de outra excursão ou sem inscrição esperada retorna 404. A expiração é reavaliada a cada consulta/emissão SSE.

O identificador de credencial no registro interno de canais não é segredo de acesso. Não se armazena o token da inscrição nas conexões SSE. Cabeçalhos de consulta incluem `Cache-Control: no-store`; eventos também usam `X-Accel-Buffering: no`.

## Regras compartilhadas

`RegrasGps` e `AmostraGps` são usados pelas viagens existentes e pelas execuções, preservando os contratos escolares. TTL de 30 segundos pelo instante observado, amostras no máximo a cada cinco segundos, validação de precisão/coordenadas/velocidade/instantes, normalização a microssegundos e ordenação monotônica. Repetição idêntica não renova TTL; reutilização do ID com outros dados retorna 409. Não se aceita amostra anterior ao início. A posição expirada não retorna coordenadas, precisão nem velocidade.

Resposta: `execucaoId`, `estado`, `latitude`, `longitude`, `observadoEm`, `recebidoEm`, `precisao`, `velocidade`, `consultarAposSegundos: 10`. Estados: `SEM_LOCALIZACAO`, `ATUALIZADA`, `DESATUALIZADA`, `FINALIZADA` (também para cancelamento). A resposta mantém o nome `execucaoId`; não confundir com ID de viagem.

O mesmo `PosicaoStreamService` atende ambos os contextos: até dois canais por principal, 200 globais, reavaliação a cada dez segundos, duração máxima de cinco minutos ou expiração da sessão, o que vier primeiro. Ao reconectar, reapresentar credencial válida. Erro de autorização encerra o canal. Polling a cada dez segundos é alternativa ao SSE; o cliente deve escolher apenas um e parar ao sair da tela ou encerrar a sessão.

Envio de posição, finalização e cancelamento compartilham o lock da excursão. Encerramento limpa a posição na mesma transação; novas amostras recebem 409. O aplicativo continua responsável por interromper GPS após encerramento, logout ou perda de permissão, e por solicitar permissões do sistema operacional. Segundo plano e bateria em dispositivos reais não foram testados.

## Recuperação e privacidade

Antes da migração: backup e teste isolado. Em rollback da aplicação, desativar as rotas novas e preservar a tabela/IDs. Não publicar posições por convites ou pelo catálogo. Políticas específicas de menores, consentimento dos responsáveis e revisão legal de transporte escolar continuam pendentes de definição/validação do produto; este recurso não presume que uma inscrição dispense essas políticas.
