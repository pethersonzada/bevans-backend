# Solicitações próprias — backend #35

GET /turmas/solicitacoes exige passageiro autenticado e deriva a identidade da
sessão Firebase. Retorna apenas id, turmaId, turmaNome e status dos seus vínculos.
Não recebe alunoId arbitrário, não retorna nomes/endereços de outros passageiros.

DELETE /turmas/solicitacoes/{id} cancela PENDENTE, preservando o vínculo e seu ID
com status CANCELADO. Repetição é idempotente (204). APROVADO/REJEITADO retorna
409; solicitação alheia retorna 403. Service revalida titular sob lock da turma.
Cancelamento e análise usam o mesmo lock, de forma que um estado final não seja
sobrescrito pela operação concorrente. Pedido posterior por código reutiliza o
mesmo vínculo cancelado, passando a PENDENTE.

Verificação: suíte padrão 112 testes passou; perfil PostgreSQL 6 testes passou,
incluindo cancelar/aprovar simultaneamente. Sem push/deploy/produção.

Parte pública de #35 ainda depende da publicação #33: pedir por ID de rota
publicada sem conhecer convite privado, validar disponibilidade/vaga e manter
turmas privadas fora do catálogo. Não fechar a issue antes dessa integração.
