# Homologação isolada do BeVans no Railway

Esta configuração prepara o backend Spring Boot para um **serviço de teste separado**. Não configura nem altera a infraestrutura da API de produção.

## Verificações anteriores ao deploy

1. Criar projeto Railway **de homologação** e ambiente com nome explícito (por exemplo, `homologacao`), sem vincular a serviços de produção.
2. Criar um serviço PostgreSQL **vazio e exclusivo** para homologação. Registrar o host, banco e usuário gerados pelo Railway sem copiar tabelas, backups, contas ou segredos de produção.
3. Preparar o schema exclusivamente nesse banco de teste usando as migrações versionadas em `docs/migrations`. Aplicar em ordem, conferir checksums e executar testes Postgres; `HIBERNATE_DDL_AUTO=validate` jamais cria ou migra tabelas.
4. Criar um **novo projeto Firebase Authentication** e identidades descartáveis de MOTORISTA e PASSAGEIRO. Configurar a credencial Firebase Admin somente no serviço backend via mecanismo de segredo/arquivo protegido do ambiente. Atualmente a aplicação usa `app.firebase.credentials-path` para ler arquivo físico (não aceita automaticamente a JSON do serviço em uma variável). Garantir montagem segura desse arquivo no contêiner ou revisão de integração de segredos antes de ativar login. Jamais criar arquivo no Git.
5. Criar serviço Railway a partir do repositório `pethersonzada/bevans-backend`, branch de homologação aprovada. O `Dockerfile` gera Java 21, o `railway.json` configura healthcheck `GET /health`, e o Spring Boot passa a escutar `PORT` com fallback 8080.
6. Variáveis de ambiente **do backend de homologação**:
   - `DATABASE_URL`: **URL JDBC** `jdbc:postgresql://HOST:PORT/BANCO` exclusiva do banco de testes. Não confundir com `DATABASE_URL` nativo em formato `postgresql://` do plugin Railway. Validar que o host não pertence à produção.
   - `DATABASE_USERNAME`, `DATABASE_PASSWORD`: credenciais da instância de teste, preferencialmente role de aplicação com privilégios mínimos e separados da role de migração.
   - `HIBERNATE_DDL_AUTO=validate`; `OPENAPI_ENABLED=false`; `SPRING_PROFILES_ACTIVE=staging`.
   - `APP_CORS_ALLOWED_ORIGINS`: origem HTTPS exata do Preview aprovado `https://bevans-webapp-git-staging-pethersonzadas-projects.vercel.app`, sem `*` ou domínios de produção.
   - `APP_NOTIFICATIONS_WORKER_ENABLED=false`, `APP_PUSH_ENABLED=false`, `APP_CONTAS_EXCLUSAO_WORKER_ENABLED=false`, `APP_CONTAS_EXCLUSAO_REMOTA_ENABLED=false`.
   - `APP_FIREBASE_ENABLED` e `APP_FIREBASE_CREDENTIALS_PATH`: habilitar Firebase somente após instalar credencial **da conta de serviço do projeto de teste** no caminho restrito; não compartilhar chaves entre ambientes.
   - `MOTORISTA_CADASTRO_CODIGO`: um valor **novo e exclusivo de homologação** armazenado como segredo. Não cadastrar motoristas reais.
7. Antes de aprovar, conferir que o domínio Railway identifica staging, `GET /health` retorna 200, banco valida schema e inicialização, e endpoints privados retornam 401/403 sem autenticação. `/health` é apenas **liveness** e não atesta banco, Firebase ou OR-Tools.
8. Atribuir URL HTTPS da API de homologação em `NEXT_PUBLIC_BEVANS_API_URL` **somente na branch Preview staging** da Vercel. Definir também a API key do Firebase de teste e token público Mapbox com domínio restrito. A API key web do Firebase não é uma credencial Admin.
9. Definir `BEVANS_STAGING_WEB_URL` e `BEVANS_STAGING_API_URL` nas GitHub Actions variables, secrets de contas descartáveis no ambiente GitHub `homologacao` com required reviewers e executar `Jornadas reais de homologação (somente leitura)` com o SHA exato do Preview.

## O que a integração automatiza

- `railway.json`: seleção do Dockerfile e healthcheck da inicialização.
- `application.properties`: suporte à variável de porta `PORT` injetada pela plataforma.
- O CI atual do backend continua testando Java 21, regras e integrações PostgreSQL isoladas **antes** do deploy.

## O que permanece externo e sujeito a autorização

- Criação real do projeto Railway/PostgreSQL e seu domínio HTTPS, variáveis/segredos, Firebase separado, migrações SQL reais e contas descartáveis.
- Configuração de DNS e CORS exato; revisão de parâmetros de tráfego, segurança de rede e acesso administrativo.
- Homologação ponta a ponta de operação, GPS, concorrência, excursões e retenção de dados pessoais.
- Testar volumes e arquivos secretos com segurança. **Não** transformar credencial Firebase Admin em `NEXT_PUBLIC_*`.

O healthcheck Railway precisa da porta informada em `PORT`; falhas de schema ou Firebase devem impedir a inicialização em vez de simular um serviço saudável. Nenhuma instrução deste documento autoriza aplicar migrações em produção.
