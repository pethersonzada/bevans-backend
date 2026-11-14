# Inscrições, credenciais e vagas — backend #30

## Jornada sem login Firebase

1. Consultar `GET /publico/excursoes/convite`, header `X-Convite`.
2. `POST /publico/excursoes/credenciais` com o mesmo header emite uma credencial
   individual aleatória de 256 bits. Guardá-la **antes** de enviar o cadastro;
   só o hash fica no banco. Esta emissão não reserva vagas e vale 30 minutos,
   limitada à partida. Até 2000 credenciais emitidas por excursão (inclui antigas).
3. `POST /publico/excursoes/inscricoes`, headers `X-Convite` e `X-Inscricao`
   (credencial do passo 2), body abaixo. Uma credencial só cria uma inscrição.
4. `GET /publico/excursoes/inscricao` ou `DELETE` no mesmo caminho, somente header
   `X-Inscricao`, consulta/cancela a própria inscrição. Não aceita IDs/token na URL.

```json
{"nome":"Participante","contato":"contato@example.invalid",
 "paradaId":null,"nomePonto":"Meu ponto confirmado",
 "latitude":-8.12,"longitude":-35.12,
 "pontoConfirmado":true,"aceitePrivacidade":true}
```

PORTA_A_PORTA exige pin confirmado e nome, sem paradaId. PARADA_UNICA permite
paradaId omitido para selecionar o único ponto ativo. MULTIPLAS_PARADAS exige
paradaId ativo da própria excursão. Nos modos de parada, omitir nomePonto,
latitude, longitude e pontoConfirmado: não enviar residência.
Contato aceita email ou telefone (8–15 dígitos); normalizado para comparação.
Nome normalizado + contato identificam duplicidade ativa, permitindo familiares
com nomes diferentes no mesmo contato. Não é verificação de identidade/telefone.
Aceite de privacidade é registrado com instante; não comprova consentimento de
responsável legal. Fluxo de consentimento verificado de menores continua pendente
antes de habilitar cadastro escolar/menores em produção.

## Reserva, confirmação e concorrência

SOLICITADA reserva um assento por 15 minutos (ou até a partida). CONFIRMADA ocupa
uma vaga sem expirar a reserva. CANCELADA/EXPIRADA não ocupam. Confirmação é do
organizador autenticado, não check-in físico nem pagamento. Cada registro ocupa
um assento; não aceita quantidade arbitrária no corpo.

Repetir cadastro com mesma credencial e dados retorna o mesmo ID/estado sem
renovar reserva. Dados diferentes com credencial usada retornam 409. Reserva
expirada não pode ser confirmada; uma nova credencial pode criar nova inscrição.
Antes de novo cadastro, expiradas são marcadas sob lock, liberando o índice
único parcial sem apagar histórico. Contagem usa validade atual, mesmo antes
de qualquer nova requisição. Cancelamento é idempotente e não reativa reserva.

Após cadastro, credencial vale até sete dias após retorno ou partida. Continua
permitindo consulta/cancelamento próprio se o convite original for revogado;
revogar convite bloqueia novos cadastros, não remove titulares existentes.
Perder credencial não autoriza recuperá-la por contato ou ID: é necessário futuro
fluxo de recuperação com verificação. Nunca compartilhar credencial individual.
Todas as respostas públicas são no-store; não incluem lista de outros inscritos.
Convite/credencial não autorizam GPS ou administração de excursão/turma.

## Administração privada pelo organizador

- `GET /excursoes/{id}/inscricoes?pagina=0&tamanho=20` (máximo 50).
- `PUT /excursoes/{id}/inscricoes/{inscricaoId}/confirmacao` confirma reserva válida.
- `DELETE /excursoes/{id}/inscricoes/{inscricaoId}` rejeita/cancela antes da partida.
- `GET /excursoes/{id}/inscricoes/vagas` retorna capacidade, reservas, confirmadas
  e disponíveis. Consulta pública de convite informa apenas vagas disponíveis;
  isso é um retrato, não garantia de reserva até cadastro transacional.

Todos exigem Firebase/MOTORISTA e titularidade no serviço. Locks da mesma linha
Excursao serializam reservas, confirmação, cancelamento, edição e mudanças de modo.
Reduzir capacidade abaixo de SOLICITADAS válidas + CONFIRMADAS retorna 409.
Com inscrição ativa, mudança de origem/destino/data/horário/modo/paradas é recusada
com 409. Título e capacidade compatível continuam editáveis. Cancelar excursão
cancela logicamente inscrições e impede novos convites/cadastros. Após primeiro
início de execução ou partida,
cadastro/alteração/confirmação/cancelamento individual são bloqueados; consulta
própria permanece até expiração da credencial. Ciclo de execução implementado no
incremento 012: excursoes-execucao-api.md.

## Persistência e proteção

Migração 011 após 010: credenciais hashadas, inscrições com consentimento,
snapshots privados do ponto e índice único parcial por participante/estado.
Nenhum script 001–010 foi reescrito. Upgrade de schema é validado em PostgreSQL
sem completar DDL por Hibernate update; índice SQL é testado com SQLSTATE 23505.
Histórico preservado por estados, sem DELETE físico. Hash de contato/nome
continua dado pessoal derivado, não deve ser tratado como anonimização.

Rate limit do prefixo público também cobre credenciais, cadastro e consulta:
60/min por IP direto e 600/min globais nesta VM. Tokens apenas nos headers;
DTOs com segredos e dados privados ocultam conteúdo em toString. Não habilitar
logs de headers/corpos sensíveis. Sem SMS, email ou push automático nesta etapa.

Testes H2/PostgreSQL e HTTP real cobrem três modos, expiração, IDOR, repetição,
última vaga concorrente, mesma credencial concorrente, confirmação/cancelamento,
edição segura e recusa de acesso administrativo/GPS por credencial individual.
Roteiro OR-Tools e execução estão em excursoes-roteiro-api.md/excursoes-execucao-api.md.
Check-in/desembarque compartilhados e notificações continuam pendentes #46/#47.
