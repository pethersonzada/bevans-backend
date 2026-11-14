# Responsáveis: vínculo técnico, consentimento e comprovação

Incremento #26, migração 017 após 001–016. Preserva MOTORISTA/PASSAGEIRO; cadastro
Firebase próprio aceita RESPONSAVEL, que sozinho não concede nenhum privilégio.
ADMIN/GESTOR continuam proibidos no cadastro e não foram implementados.

## Fluxo e contratos

Todas as rotas exigem Firebase; ator é derivado da sessão, nunca do corpo.

| Método | Rota | Permissão/efeito |
|---|---|---|
| POST | `/responsaveis/convites` | Passageiro emite convite para si, válido 15 minutos; revoga os anteriores pendentes |
| DELETE | `/responsaveis/convites` | Passageiro revoga seus convites não consumidos |
| POST | `/responsaveis/vinculos` | Responsável apresenta `{token, aceite:true, termoVersao:"1"}`; cria solicitação, sem acesso a dados |
| GET | `/responsaveis/vinculos?pagina=0&tamanho=20` | Somente vínculos da própria conta, máximo 50 itens |
| PUT | `/responsaveis/vinculos/{id}/aprovacao` | Passageiro do vínculo apresenta `{versao, aceite:true, termoVersao:"1"}` |
| PUT | `/responsaveis/vinculos/{id}/revogacao` | Uma das partes retira autorização/consentimento com `{versao}`; repetição é idempotente |
| GET | `/responsaveis/vinculos/{id}/historico` | Auditoria paginada acessível somente às duas partes, mesmo após revogação |
| GET | `/responsaveis/vinculos/{id}/operacao?viagemId=...` | Responsável comprovado recebe apenas o registro operacional do vinculado, em viagem ativa |
| GET | `/responsaveis/vinculos/{id}/localizacao?viagemId=...` | Mesma autorização, posição pelo serviço GPS existente, sem novo canal |

Resposta do vínculo: IDs das duas partes, estado, versão, instantes de aceites,
versões de termo, verificação e revogação. Não retorna telefone, email, endereço,
documento comprobatório nem dados de terceiros. Convite retorna `{token,expiraEm}`
uma única vez; o banco guarda somente SHA-256. Token não deve ir para URL/logs.
Uso duplicado/expirado/revogado retorna 404; recuperar solicitação já feita via
lista própria. Convite vazado não ativa vínculo: ainda exige aceite do passageiro
e comprovação confiável. Identificador de passageiro enviado pelo responsável
não substitui convite. Um vínculo revogado exige novo convite e novo fluxo.

Estados: SOLICITADO → AGUARDANDO_VERIFICACAO ou ATIVO → REVOGADO. Nenhuma mutação
reativa REVOGADO sem novo convite. Há unicidade por par, lock do passageiro e
controle de versão. Concorrência entre aprovação/revogação termina de acordo com
a primeira transação e recusa versão obsoleta com 409; revogar a versão vigente
retira o acesso imediatamente. Alterações mantêm eventos, autoria e timestamps.

## Bloqueio real de produto/identidade

O adaptador de produção `VerificacaoResponsabilidadePendente` **nega comprovação**.
Não existe flag HTTP, papel autoproclamado, endpoint administrativo ou propriedade
que transforme declaração do cliente em responsabilidade legal comprovada.
Aceite pelo passageiro registra somente autorização técnica: sem comprovação, a
aprovação fica AGUARDANDO_VERIFICACAO e **não libera operação ou GPS**.

Para habilitar futuramente: definir com o produto o processo de comprovação de
identidade/responsabilidade, consentimento de menores, termo efetivo e retenção;
integrar um adaptador confiável do servidor `VerificacaoResponsabilidade`, com
referência verificável e política de revogação/indisponibilidade. O adaptador é
reconsultado antes de cada leitura; indisponibilidade/prova revogada nega acesso.
Não enviar documentos/PII como referência; ela é interna e não aparece na API.
Os testes positivos usam um adaptador **somente de teste**; não comprovam parentesco
real nem validade jurídica. A issue permanece aberta por essa dependência externa.

Versão de termo é configuração do servidor `app.responsaveis.termo-versao` (padrão
`1`, identificador técnico, não texto jurídico aprovado). Ambas as partes devem
aceitar a versão vigente; mudança exige renovar o fluxo, nunca aceite implícito.

Leituras verificam também papel, matrícula aprovada e inclusão na operação daquele
passageiro. Não retornam painel/lista de passageiros nem habilitam os endpoints
legados do motorista. Responsável não confirma embarque, não modifica endereço/
presença do passageiro e não lê excursões anônimas por ID: esses escopos exigem
contratos próprios aprovados. GPS mantém TTL/limites existentes; polling sugerido
de 10 segundos, parar no logout/revogação/encerramento. Não há push de responsável
presumido nem herança das notificações privadas de outra conta.

## Migração e recuperação

017 cria convites, vínculos e auditoria com FKs/unicidade; ATIVO exige os dois
aceites da mesma versão e referência verificada. Não altera tipos de usuários
existentes nem faz associação/backfill automático. Aplicar primeiro em cópia
isolada e validar com Hibernate. Backup antes de futura implantação; rollback de
aplicação desativa o fluxo e preserva tabelas/consentimentos/auditoria, sem DROP.
