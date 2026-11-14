# BeVans - Documentação Técnica do Backend

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)

> ⚠️ **Aviso de Propriedade Intelectual:** Este repositório é de uso estritamente pessoal e acadêmico, mantido no GitHub apenas para controle de versão do projeto **BeVans**. A cópia, distribuição, comercialização ou uso não autorizado do código-fonte por terceiros é expressamente proibida.

## 1. Visão Geral da Arquitetura do Backend e Padrões de Projeto

O ecossistema backend do **BeVans** é estruturado como uma API RESTful de alta performance e escalabilidade horizontal, desenvolvida em Java utilizando o ecossistema Spring Boot. O núcleo do sistema é responsável por processar o fluxo transacional crítico de rotas de transporte escolar, gerenciar vínculos relacionais complexos entre motoristas, turmas, escolas e passageiros, e garantir a persistência íntegra, segura e acidizada (ACID) dos dados operacionais em tempo real.

A arquitetura segue estritamente o padrão de camadas desacopladas (Presentation/Controller, Business/Service, Persistence/Repository, e Domain/Entity), promovendo inversão de controle e injeção de dependência gerenciada pelo container Spring. Isso garante baixo acoplamento entre os componentes, testabilidade unitária e facilidade de manutenção evolutiva.

## 2. Autenticação, Segurança e Regras de Acesso

Todas as rotas protegidas exigem obrigatoriamente o envio do token JWT emitido pelo Firebase Authentication no cabeçalho HTTP de autorização:

```
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

### Regras Críticas de Negócio:
* **Validação de Papel (*Role-Based Access Control*):** O backend NUNCA confia cegamente no tipo de usuário enviado pelo payload do cliente. O registro e privilégios de motoristas passam por validação estrita de chaves de autorização ou regras de negócio no servidor.
* **Idempotência:** Endpoints transacionais críticos implementam tratamento contra duplicidade, evitando registros órfãos no banco de dados em caso de instabilidade na rede mobile.

## 3. Principais Endpoints (Contratos de API)

### 3.1 Usuários
| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `POST` | `/usuarios/cadastrar` | Registra o perfil do usuário no banco relacional após autenticação no Firebase. |
| `GET` | `/usuarios/por-uid/{uid}` | Retorna as informações cadastrais do usuário filtradas por UID. |
| `GET` | `/usuarios/passageiros` | Lista todos los passageiros vinculados e seus respectivos status de presença. |
| `DELETE` | `/usuarios/{id}` | Própria conta sem vínculos: 202 com tarefa remota pendente; histórico vinculado bloqueia com 409. |

### 3.2 Turmas e Vínculos
| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `POST` | `/turmas` | Cria uma nova turma (Nome, Turno, ID do Motorista). |
| `PUT` | `/turmas/{id}` | Atualiza dados cadastrais, origem (garagem) e destino da turma. |
| `POST` | `/turmas/entrar/{userId}?codigo=XXXXXX` | Processa a solicitação de vínculo do passageiro via código de convite. |
| `PUT` | `/turmas/analisar/{vinculoId}?status=APROVADO` | Aprova ou rejeita o vínculo do aluno requisitante. |
| `PUT` | `/turmas/{id}/regenerar-codigo` | Invalida a chave atual e gera um novo código alfanumérico de 6 caracteres. |

### 3.3 Rotas e Geolocalização
| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `GET` | `/rota/status-atual?turmaId=` | Identifica se há viagem em andamento e o sentido (`IDA` / `VOLTA`). |
| `GET` | `/rota/otimizar?sentido=&turmaId=` | Executa o algoritmo de ordenação de paradas com base nas confirmações. |
| `POST` | `/rota/iniciar` | Inicializa o ciclo de viagem para a turma informada. |
| `POST` | `/rota/localizacao-van` | Legado desativado (410); usar `PUT /viagens/{id}/localizacao`. |
| `GET` | `/rota/localizacao-van` | Legado desativado (410); usar `GET /viagens/{id}/localizacao` ou SSE autenticado. |
| `POST` | `/rota/confirmar?usuarioId=&status=&enderecoId=` | Atualiza a intenção de presença diária do passageiro. |
| `POST` | `/rota/encerrar` | Encerra formalmente o ciclo de viagem ativo. |

## 4. Pré-requisitos, Instalação e Configuração

### 4.1 Requisitos de Ambiente
* Java Development Kit (JDK) 21 (versão definida no Maven).
* Apache Maven 3.8+.
* Banco de Dados PostgreSQL 14+.
* Conta Google Firebase configurada com Firebase Admin SDK.

### 4.2 Configuração do ambiente

`application.properties` exige `DATABASE_URL`, `DATABASE_USERNAME` e
`DATABASE_PASSWORD`; o padrão de `HIBERNATE_DDL_AUTO` é `validate`.
Configurar `app.firebase.credentials-path` com arquivo externo protegido, nunca
versionado. Arquivo `.env` não é carregado automaticamente pelo Spring Boot.
Consultar [preparação de produção](docs/preparacao-producao.md) para flags,
backup, inventário de migrações e validação de SSE/FCM antes de implantação.

## Contratos e validação incremental

- [Auditoria e evidências locais](docs/auditoria-e-execucao.md)
- [Capacidade por turma, migração e recuperação](docs/capacidade-turmas.md)
- [Estado exato e próximos passos](docs/retomada.md)
- [Coleta e paradas de Excursões](docs/excursoes-coleta-api.md)
- [Convites seguros](docs/excursoes-convites-api.md)
- [Inscrições e vagas](docs/excursoes-inscricoes-api.md)
- [Roteirização de Excursões](docs/excursoes-roteiro-api.md)
- [Execução persistida](docs/excursoes-execucao-api.md)
- [Coleta e paradas de turmas privadas](docs/turmas-coleta-api.md)
- [Controle compartilhado de embarque e desembarque](docs/controle-operacional-api.md)
- [Central de notificações e dispositivos](docs/notificacoes-api.md)
- [GPS de execuções de excursões](docs/excursoes-gps-api.md)
- [Alertas de proximidade e resumos operacionais](docs/proximidade-api.md)
- [Responsáveis e consentimento verificável](docs/responsaveis-api.md)
- [Contato comercial protegido](docs/contato-motorista-api.md)
- [Identidade Firebase e perfis legados](docs/identidade-firebase-api.md)
- [Exclusão transacional de contas](docs/exclusao-contas-api.md)
- [OpenAPI e ativação segura](docs/openapi-api.md)
- [Preparação de produção](docs/preparacao-producao.md)
- [Avaliação futura de ADMIN/GESTOR](docs/papeis-futuros.md)

O cadastro de turma aceita rascunho sem percurso e exige `capacidade` inteira
positiva. Origem/destino são confirmados por coordenadas no mapa, em PUTs
separados. `GET /turmas/{id}/vagas` retorna ocupação para o motorista responsável.
Turmas antigas sem capacidade preservam dados e exigem configuração antes de
novas aprovações. Não executar migrações em produção sem validação e autorização.

