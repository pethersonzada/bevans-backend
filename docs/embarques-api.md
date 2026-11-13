# Embarques privados em Rotas Públicas — backend #40

Pedido de embarque não é outra matrícula: aprovação reutiliza AlunoTurma e o
controle de vagas de #35. Dois sentidos/datas do mesmo passageiro aprovado não
ocupam dois assentos na turma recorrente. Não há lista de espera automática.

| Método | Caminho | Autorização |
| --- | --- | --- |
| POST | /rotas-publicas/{id}/embarques | Passageiro autenticado, rota publicada/ativa |
| GET | /embarques/minhas | Somente pedidos do passageiro da sessão |
| DELETE | /embarques/{id} | Titular, somente PENDENTE; CANCELADO é idempotente |
| GET | /rotas-publicas/{id}/embarques?data=AAAA-MM-DD&sentido=IDA | Motorista dono |
| PUT | /embarques/{id}/analise | Motorista dono; body {status:APROVADO ou REJEITADO} |
| POST | /rotas-publicas/{id}/embarques/planejamento?data=AAAA-MM-DD&sentido=IDA&otimizar=true | Motorista dono, antes da partida |

Corpo do pedido: dataOperacao (ISO), sentido IDA/VOLTA e observacao opcional
(até 500 caracteres). FIXA exige paradaId existente e ativa da própria rota,
sem coordenadas/nome substitutos. FLEXIVEL aceita a mesma seleção OU nomePonto,
latitude, longitude e pontoConfirmado=true. Coordenadas finitas e dentro dos
limites; nenhum geocoder ou centro de rua é atribuído pelo backend.

Dias e horários precisam corresponder à operação cadastrada. Solicitar/analisar/
cancelar/planejar exige data entre hoje e 30 dias (antecedência configurável) e
horário anterior à partida no fuso app.time-zone, padrão America/Sao_Paulo.
Qualquer viagem ativa da turma impede essas alterações. Aprovação repetida com
mesmo resultado é idempotente; transição incompatível retorna 409. Identidade
sempre vem do Firebase, nunca de alunoId enviado no corpo.

Um pedido idêntico aberto para passageiro/rota/data/sentido retorna o mesmo ID;
outro ponto nesse contexto retorna 409. Cancelado/rejeitado permanece no histórico
e novo pedido recebe novo ID. PostgreSQL reforça unicidade de PENDENTE/APROVADO
com índice parcial. Cancelamento de embarque já aprovado exige fluxo futuro de
desistência/replanejamento; não remover matrícula silenciosamente.

Pin residencial solicitado permanece privado, inclusive após aprovação. Não é
copiado para paradas_publicas e nunca é retornado pelo catálogo. Somente a prévia
privada da execução considera esses pontos aprovados com matrícula ainda ativa.
Pendentes/rejeitados/cancelados não entram no percurso. FIXA mantém ordem pública;
FLEXIVEL pode otimizar pelo OR-Tools com origem/destino preservados.

Parada com embarque futuro aprovado não pode ser removida/reposicionada nem ter
horário alterado silenciosamente. Modalidade/dias/horários também são protegidos;
reordenar antes da partida continua permitido. Rejeição registra ator/hora sem
ocupar vaga. Controle de aprovação/cancelamento usa o mesmo lock da turma.

Migração incremental: 003-embarques.sql; preserva IDs/tabelas/vínculos existentes.
Recuperação mantém tabela e histórico ao voltar aplicação. Não executar DROP.
Testes automatizados cobrem service/persistência, HTTP, capacidade, duplicação,
concorrência, temporalidade e privacidade, em H2 e PostgreSQL isolado.
Nenhum código de frontend foi criado ou alterado nesta implementação.

## Exceções explícitas em FIXA

`PUT /rotas-publicas/{id}/pontos-excepcionais` com `{"valor":true}` e token do
motorista responsável permite pedidos de pin confirmado em FIXA. Padrão false;
o DTO de detalhes informa `permitePontoExcepcional`. Configuração bloqueada
durante viagem. Desabilitar impede novos pins e aprovações pendentes; não cancela
embarques já aprovados. Pins continuam privados e exigem aprovação individual.
Plano FIXA preserva a ordem relativa das paradas cadastradas e inclui exceções
aprovadas antes do destino, sem otimização automática. Migração incremental 006.
