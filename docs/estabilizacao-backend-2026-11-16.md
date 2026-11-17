# Estabilização para 18/11/2026

Branch: `estabilizacao-excursoes-api-20261116`, PR #62. Preservadas as correções anteriores da PR. Frontend #47 consultado em `81e9d8e`; nenhum arquivo daquele repositório foi alterado.

| Problema / causa | Correção e arquivos principais | Regressão |
| --- | --- | --- |
| #59: endereço ausente, de terceiro ou inválido era descartado silenciosamente | `RotaService`: conflito explícito por sentido; `ColetaPrivadaService`: validar também porta a porta antes da partida | `RotaServiceTest`, `OperacaoIntegrationTest`: erro 409, sem lista parcial nem viagem persistida; VOLTA usa endereço próprio |
| Roteiro ativo continha só passageiros, omitindo extremos e paradas sem reservas | `OperacaoService`, `OperacaoViagem`, `OperacaoController`, migração 021: snapshot completo salvo atomicamente e consulta exclusiva do dono | FIXA/FLEXIVEL, IDA/VOLTA, extremos próprios, paradas vazias, exceção aprovada, pendentes excluídos, parada desativada, isolamento e imutabilidade em H2/PostgreSQL |
| Planejamento privado atravessava thread assíncrona durante a transação de partida | `RotaService.planejarPrivada`: cálculo síncrono interno, separado da consulta do snapshot ativo | Partida com confirmados, rollback por ponto inválido, concorrência e nova execução |
| #60/#61: consulta e partida de excursões tinham regras pouco claras e conflitos genéricos | Preservada consulta histórica; `RoteiroExcursaoService`, `ExecucaoExcursaoService`, `GlobalExceptionHandler`: códigos de conflito, validação de pontos e regra explícita de atraso | Antes/no horário/depois, confirmados, três modos sem inscritos, extremos, concorrência, IDA/VOLTA, cancelamento e autorização; testes HTTP do campo `codigo` |
| Status inativo não distinguia ausência de execução de encerramento | `ViagemService`, `ViagemRepository`: campos adicionais `estado`/`ultimaViagemId`; `RotaController` usa o Clock operacional | Ciclo de GPS ausente, recente, expirado, finalizado e segunda viagem sem posição herdada |
| OR-Tools privado não liberava recursos nativos nem limitava duração | `RotaService`: `finally` e limite de dois segundos; indisponibilidade explícita | Testes de ordem e completude nos dois sentidos |
| Suíte inicial falhava intermitentemente na fila de exclusão por arredondamento para o futuro | `ExclusaoContaService`: truncar instante inicial para microssegundos | Relógio fixo com nanos reproduziu duas falhas antes da correção; testes existentes mantidos e fila isolada por teste, também no PostgreSQL |
| PostgreSQL com LC_CTYPE=C não normalizava Ô/Á/Ç na busca | `CatalogoService`: traduzir acentos de ambas as caixas antes de lower | Teste existente de filtros combinados reproduziu falha no PostgreSQL C/UTF-8; normalização corrigida sem mudar expectativas |

Contrato de integração: [roteiro-operacional-api.md](roteiro-operacional-api.md).

## Validação e limites

Java 21: Maven `clean verify` aprovado com **342 testes** e empacotamento; perfil `postgres-validation` aprovado com **140 testes**, incluindo upgrade integral das migrações 001–021 com Hibernate `validate`. `git diff --check` aprovado. PostgreSQL 16.4 exclusivo de teste, ouvindo em 127.0.0.1:55435, locale C/UTF-8; sem conexão a dados de produção. Logs locais em `.validation-logs/estabilizacao-entrega.log` e `.validation-logs/estabilizacao-postgres-entrega.log` (ignorados pelo Git). CI remota será vinculada na PR após publicar os commits.

Testes de autorização HTTP usam Firebase simulado. Não há evidência de homologação HTTP com credenciais reais nem aceite Android em dispositivos nesta rodada. Manter issues de integração abertas. A migração 021 precisa integrar o procedimento operacional antes da implantação; nenhum deploy, merge ou alteração de banco real foi realizado.
