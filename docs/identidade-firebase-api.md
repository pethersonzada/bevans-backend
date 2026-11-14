# Associação segura de identidade Firebase

Correção #49, identificada na revisão #6/#27. Reproduzido perfil legado sem UID
sendo associado apenas por email não verificado (HTTP 200 em vez de 403). O log
`.validation-logs/seguranca-regressoes-reproduzidas.log` contém o teste negativo
original, com Firebase simulado e dados sintéticos, sem uso de credenciais reais.

`POST /auth/login` continua recebendo `{email}` e retornando id/tipo/nome/endereco.
Identidade vem do ID token, nunca do corpo. O serviço prefere perfil já vinculado
ao UID; não muda o papel nem associa por IDs do cliente. Email do corpo deve
corresponder ao claim do token. Associação de um perfil legado ainda sem UID
exige claim **email_verified=true** do Firebase, lock e revalidação do registro.
Outro UID não pode tomar o perfil; corrida entre duas identidades tem uma única
associação vencedora. Perfis já vinculados mantêm login sem exigir nova confirmação
de email, inclusive após mudança do email local; UID é a identidade canônica.

O filtro agora usa `verifyIdToken(token,true)`: tokens de conta revogada/desativada
são rejeitados antes de consultar dados. Falha transitória do provedor retorna
503, sem tratar indisponibilidade como logout/token inválido. O cliente deve tentar
novamente com espera; não apagar sessão por um 503. Token inválido/revogado é 401;
tentativa de associação sem prova ou a outro perfil é 403.

Migração 019 cria índice único para firebase_uid não nulo. Um preflight falha com
mensagem genérica se houver duplicidade; **não remove nem reconcilia usuários**.
Perfis legados com UID null permanecem. Cadastro tem validação antecipada e erro
409 sanitizado para disputa de unicidade. Teste PostgreSQL comprova proteção no
banco mesmo fora do controller. Reconciliar eventuais duplicatas em cópia isolada
com procedimento aprovado antes da migração; backup e validate obrigatórios.

Não há prova de parentesco, papel administrativo ou autorização de motorista
derivados somente de email. RESPONSAVEL continua sujeito ao vínculo e verificador
próprios; MOTORISTA mantém o provisionamento existente com segredo do servidor.
Não executar testes com Firebase/contas de produção. Revogação real no provedor
continua sendo um critério de integração externa; os testes locais verificam
contrato do SDK, decisão de autorização, concorrência e persistência.
