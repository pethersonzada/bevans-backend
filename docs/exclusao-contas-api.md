# Exclusão de conta: integridade, retenção e provedor

Correção #50 identificada em #6/#27. O comportamento antigo chamava Firebase
deleteUser antes de descobrir FKs no PostgreSQL; o teste original provou chamada
remota seguida de falha local. Nenhuma conta real foi removida nos testes.

## Contrato atualizado

`DELETE /usuarios/{id}` exige Firebase do próprio usuário. Outra conta é 403;
sem token, 401. Não aceita UID alternativo no corpo. **202 Accepted**, com mensagem
de exclusão remota pendente, substitui o antigo 200 que afirmava conclusão.
Frontend deve tratar 202 como solicitação registrada e encerrar sessão local,
sem afirmar que a identidade Firebase já foi apagada.

O serviço bloqueia remoção se houver matrículas, histórico operacional,
notificações, consentimentos, endereços ou outros relacionamentos protegidos por
FK: 409, mantendo perfil e identidade. Não se apagam histórico de menores,
viagens/presenças, auditoria ou vínculos em cascata para fazer a exclusão passar.
**Pendente de produto:** retenção, pseudonimização, direitos de titulares e fluxo
de atendimento dessas contas. Esta correção não declara conformidade jurídica.

Para conta sem dados vinculados: transação remove perfil local, valida integridade
com flush e grava outbox `exclusoes_conta`. Falha/rollback preserva perfil e impede
qualquer tarefa remota. O UID fica reservado nessa outbox e não pode ser recadastrado
ou usado para associar outro perfil. Nenhuma chamada externa ocorre na transação.

## Consumo remoto controlado

Por padrão, **ambos desativados**:

- `app.contas.exclusao-worker.enabled`: consumidor de tarefas após commit;
- `app.contas.exclusao-remota.enabled`: adaptador Firebase efetivamente destrutivo.

Não ativar em produção sem aprovação explícita, política de atendimento/recuperação
e conferência das tarefas pendentes. Não foram ativados nesta sessão. O adaptador
usa Firebase Admin existente; nenhuma credencial nova ou real foi adicionada.

Worker processa até cinco tarefas a cada dez segundos; reserva transacional com
lease de 120 s, resposta vinculada à tentativa e retries limitados a cinco com
espera crescente. Adaptador desativado ou Firebase não configurado não consomem tentativas reais de
exclusão: adiam uma hora. O envio FCM é independente desse fluxo de identidade.
Estado PENDENTE/PROCESSANDO/CONCLUIDA/FALHA permanece no banco. Conta já ausente no
provedor é sucesso idempotente; retorno tardio de reserva vencida não conclui a
reserva seguinte. Falha após sucesso remoto pode repetir chamada, cujo resultado
USER_NOT_FOUND finaliza a tarefa; não se promete exatamente uma chamada de rede.

Não há endpoint público de consulta/reenvio da fila ou forma de apagar conta de
terceiro. Logs/DTOs não devem revelar UID. O registro contém apenas UID, estado e
metadados necessários ao processamento, não email/telefone/JWT. Definir retenção
desse identificador antes de habilitar o fluxo em produção.

## Migração/recuperação

020 após 019 cria tabela/índice sem backfill ou exclusão automática. Aplicar em
cópia isolada, com backup e Hibernate validate antes de implantação autorizada.
Rollback deve parar consumidor e preservar tarefas, evitando perda de ações
pendentes ou recriação de UID cuja exclusão remota já foi solicitada. Uma versão
antiga que ignora essa reserva de UID não é rollback funcional seguro: preferir
roll-forward/correção mantendo o guard de identidade.

Testes usam Firebase mock/adaptador desativado, rollback real e PostgreSQL isolado.
Não houve exclusão real no Firebase nem acesso a produção. Validação real de
provedor exige conta descartável de ambiente de testes e autorização própria.
