# ADMIN e GESTOR — avaliação para fase posterior (#37/#38)

Proposta de arquitetura para discussão, não matriz aprovada. Nenhum papel,
endpoint administrativo ou permissão global foi implementado nesta rodada.
Cadastro público aceita somente os papéis expressamente permitidos; não permite
que o cliente se promova a ADMIN/GESTOR. MOTORISTA e PASSAGEIRO são preservados.

GESTOR exige organização/frota explícita, associação de usuários com vigência,
escopo de função, veículos e atribuições de motoristas. Um motorista autônomo
continua funcionando sem organização. Não deduzir organização por email, cidade,
favorito ou vínculo de passageiro. Vários motoristas na frota não devem receber
automaticamente acesso aos passageiros e GPS uns dos outros.

| Ator futuro | Escopo proposto a aprovar |
|---|---|
| GESTOR | Frota e designações da própria organização, com atribuições auditadas |
| MOTORISTA | Turmas, viagens e participantes sob responsabilidade efetiva |
| ADMIN | Operações excepcionais explicitamente aprovadas, sem acesso irrestrito implícito a GPS/dados pessoais |
| PASSAGEIRO/RESPONSAVEL | Próprios dados e vínculos comprovados, sem ampliação por organização |

Antes da implementação, aprovar quais ações exigem reautenticação/MFA,
segregação de funções, justificativa, prazo e revisão. Provisionamento deve
ocorrer por processo confiável no servidor, nunca por `tipo` enviado no signup
ou simples claim recebido sem política. Definir primeiro administrador e sua
recuperação, suspensão de organização, revogação e auditoria sem edição retroativa.

Entidades candidatas: Organizacao, VinculoOrganizacional, Veiculo e
DesignacaoMotorista. Não introduzir agora coluna obrigatória de tenant em todas
as entidades. Planejar migrações incrementais com IDs preservados, atribuição
explícita de dados existentes e autorização que combine organização, função,
vigência e responsabilidade operacional. Uma troca de motorista precisa guardar
histórico e não transferir consentimentos legais silenciosamente.

Transações devem adotar ordem de locks documentada (organização/designação,
turma/execução e matrícula), revalidar permissões dentro da transação e preservar
as garantias de capacidade/viagem ativa. Testar organizações distintas, revogação
simultânea a uma ação, frota com vários motoristas e motorista autônomo. Queries,
catálogo, favoritos, notificações e SSE precisam de revisão de escopo; nenhum
filtro somente no frontend serve como isolamento.

Bloqueios: modelo comercial, matriz de acesso, governança de provisionamento,
retenção/auditoria e migração de responsabilidades. #37 e #38 permanecem abertas;
este documento prepara a decisão sem criar funcionalidades administrativas.
