# BeVans | API Backend

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)

API REST do **BeVans**, responsável pelo domínio de transporte escolar/universitário: usuários, turmas, vínculos, confirmações, viagens, localização e operações de excursões. O serviço aplica regras de negócio e autorização no servidor e persiste dados em banco relacional.

**Repositório:** [pethersonzada/bevans-backend](https://github.com/pethersonzada/bevans-backend)  
**Integrações:** [Mobile](https://github.com/pethersonzada/bevans-frontend) · [WebApp](https://github.com/pethersonzada/bevans-webapp) · [Agente FAQ](https://github.com/pethersonzada/bevans-agente)

## Stack

- **Java 21**, **Spring Boot 3.3.0** e **Maven**.
- Spring Web, Spring Security, Spring Data JPA/Hibernate.
- **PostgreSQL** em execução; **H2** nos testes.
- **Firebase Admin SDK** para integração com identidade Firebase.
- **Google OR-Tools 9.8** para problemas de otimização.
- **springdoc OpenAPI** para documentação interativa, desabilitada por padrão.

## Arquitetura

```text
App React Native
   | Firebase ID token
   v
Controllers REST + Spring Security
   |
Services / regras de domínio
   |
Repositories JPA + entidades
   |
PostgreSQL

Serviços auxiliares: Firebase Admin, otimização de rotas e notificações
```

Os principais contextos de negócio incluem usuários e perfis, turmas e permissões de acesso, presença diária, início/execução/fim de viagens, geolocalização e excursões. O backend é a autoridade das verificações de vínculo, capacidade e acesso aos dados.

## Requisitos

- JDK **21**.
- Maven compatível com o projeto (ou Maven Wrapper, caso esteja disponível na sua cópia).
- Instância PostgreSQL acessível e esquema/migrações alinhados à versão do código.
- Credenciais Firebase Admin configuradas fora do repositório.

## Instalação e configuração

```bash
git clone https://github.com/pethersonzada/bevans-backend.git
cd bevans-backend
```

`src/main/resources/application.properties` utiliza estas configurações:

```dotenv
DATABASE_URL=jdbc:postgresql://localhost:5432/bevans
DATABASE_USERNAME=SEU_USUARIO
DATABASE_PASSWORD=SUA_SENHA
HIBERNATE_DDL_AUTO=validate
OPENAPI_ENABLED=false
```

O projeto declara `spring.config.import=optional:file:.env[.properties]`, portanto um `.env` em formato `chave=valor` pode ser importado quando a aplicação é iniciada no diretório correto. Variáveis do ambiente do processo também são suportadas pelo Spring. Verifique em `docs/preparacao-producao.md` como configurar a credencial externa do Firebase (`app.firebase.credentials-path`) e os demais requisitos do seu ambiente.

**Atenção:** `HIBERNATE_DDL_AUTO=validate` verifica a estrutura, mas **não cria tabelas nem executa migrações**. Prepare o banco antes de iniciar. Não use `create` ou `update` indiscriminadamente em produção.

## Execução

```bash
mvn spring-boot:run
```

Porta padrão: **8080**, conforme `application.properties`. Para compilar e verificar:

```bash
mvn test
mvn package
```

O `pom.xml` também define um perfil `postgres-validation` com testes de integração específicos:

```bash
mvn -Ppostgres-validation test
```

Esse perfil requer ambiente PostgreSQL de validação devidamente preparado; não execute contra dados de produção.

## Autenticação

As rotas protegidas recebem um ID token obtido pelo cliente Firebase:

```http
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

A autenticação identifica o solicitante; **autorização** exige validações adicionais no servidor para impedir acesso a turmas, viagens e dados de terceiros. Nunca use uma chave Firebase Admin no aplicativo cliente.

## Contratos principais

A lista a seguir resume caminhos descritos pelo README e documentos do backend; consulte a implementação atual dos controllers e os contratos em `docs/` antes de integrar.

| Método | Rota | Finalidade |
| --- | --- | --- |
| `POST` | `/usuarios/cadastrar` | Registra perfil associado à identidade autenticada. |
| `GET` | `/usuarios/por-uid/{uid}` | Consulta perfil por UID, sujeito às permissões. |
| `POST` | `/turmas` | Cadastra turma. |
| `PUT` | `/turmas/{id}` | Atualiza informações da turma. |
| `POST` | `/turmas/entrar/{userId}?codigo=...` | Solicita entrada por código. |
| `PUT` | `/turmas/analisar/{vinculoId}?status=...` | Analisa pedido de vínculo. |
| `GET` | `/turmas/{id}/vagas` | Consulta ocupação/capacidade para motorista autorizado. |
| `GET` | `/rota/status-atual?turmaId=...` | Consulta estado da viagem. |
| `GET` | `/rota/otimizar?sentido=...&turmaId=...` | Calcula/consulta sequência otimizada de paradas. |
| `POST` | `/rota/iniciar` | Inicia viagem. |
| `POST` | `/rota/confirmar?usuarioId=...&status=...&enderecoId=...` | Confirma intenção de deslocamento. |
| `POST` | `/rota/encerrar` | Encerra viagem. |
| `PUT` | `/viagens/{id}/localizacao` | Atualiza localização da viagem. |
| `GET` | `/viagens/{id}/localizacao` | Consulta localização autorizada. |

**Rotas legadas:** `POST` e `GET /rota/localizacao-van` constam como desativadas (`410`) na documentação atual. Utilize os contratos de `/viagens/{id}/localizacao` e a documentação de SSE quando aplicável.

### OpenAPI

O projeto configura `springdoc.api-docs.enabled` e `springdoc.swagger-ui.enabled` com `OPENAPI_ENABLED`, cujo valor padrão é `false`. Em ambiente local controlado, defina `OPENAPI_ENABLED=true` e consulte os caminhos padrão do springdoc (`/v3/api-docs` e `/swagger-ui/index.html`), confirmando eventuais restrições do Spring Security.

## Documentação técnica complementar

Os contratos mais específicos estão em [`docs/`](https://github.com/pethersonzada/bevans-backend/tree/main/docs), incluindo:

- `preparacao-producao.md`: deploy, segredos, banco e verificações.
- `identidade-firebase-api.md` e `exclusao-contas-api.md`: identidade e exclusão.
- `capacidade-turmas.md` e `turmas-coleta-api.md`: turmas, vagas e coleta.
- `excursoes-*-api.md`: inscrições, convites, roteirização e execução.
- `notificacoes-api.md`, `proximidade-api.md` e `excursoes-gps-api.md`: notificações e localização.
- `openapi-api.md`: disponibilização segura da especificação.

## Cuidados operacionais

- Mantenha segredos, senhas e credenciais Firebase fora do Git.
- Verifique autorização por recurso e não confie no perfil declarado pelo cliente.
- Faça backup e valide migrações antes de alterações no PostgreSQL.
- Não publique OpenAPI, logs sensíveis ou endpoints operacionais sem avaliar acesso.
- Trate sinais de localização como dados sensíveis ao contexto: controle acesso, retenção e exposição.

**Base desta documentação:** `pom.xml`, `src/main/resources/application.properties`, README e índice de documentação do repositório. A lista de rotas é um panorama, não substitui uma especificação OpenAPI gerada da versão implantada.
