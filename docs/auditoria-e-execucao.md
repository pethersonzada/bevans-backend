# Auditoria e execução — 13/11/2026

## Retomada posterior — estado atual

Sem reiniciar a auditoria: os lotes foram revalidados e ampliados. PostgreSQL 16
local descartável executou migrações 001/002 e testes de capacidade/concorrência.
Backend final: **125 testes padrão aprovados**, mais **14 execuções PostgreSQL**
em perfil separado. Frontend: arquitetura, TypeScript e **72 testes aprovados**.
Não foram encontrados cinco testes removidos: baseline de 104 foi reproduzida;
não há evidência local da execução anterior de 109. Ver validacao-retomada.md.

Implementados cancelamento/consulta própria e pedido público de vaga (#35),
publicação explícita/paradas/planejamento FIXA/FLEXIVEL (#33/#39), e editor mobile
do motorista (#26). Endpoints e limites em rotas-publicas-api.md. Sem teste nativo:
ADB não encontrou aparelho/emulador. Estado exato, commits e próxima tarefa #40/#27
em retomada.md. Seções abaixo preservam o histórico anterior desta execução.

## Base examinada

- Backend: `365b930`, branch e árvore inicialmente sem alterações locais.
- Frontend: `b149874`, árvore inicialmente sem alterações locais. Git consultado
  com `-c safe.directory=C:/dev/bevans-frontend`, sem alterar configuração global.
- READMEs, documentação técnica local, modelos, repositórios, serviços,
  controllers, políticas de acesso e testes inspecionados.
- GitHub: 20 issues abertas do backend e 22 do frontend, detalhes e comentários
  coletados diretamente. Snapshot em `backlog-2026-11-13.json`; inclui também
  issues fechadas consultadas para histórico. Issue #44 criada após esta coleta.
- Nenhum fetch/merge/push/deploy ou acesso deliberado à API/banco de produção.
  Referências remotas locais não foram atualizadas.

## Diagnóstico por dependência

1. **Estabilização:** Firebase e autorização por proprietário já existem; frontend
   tem renovação de token, testes de sessão, polling com foco e serviços extraídos.
   Não reimplementar esses fluxos por ainda haver issues abertas. Cadastro de turma
   quebrado na cópia local: app cria rascunho antes do mapa, service exigia percurso.
   Campo separado de número ainda era obrigatório no componente compartilhado.
   Endereço POST não validava coordenadas nem neutralizava ID do corpo.
2. **Capacidade (#35 / frontend #20):** modelo atual não tem capacidade por turma;
   aprovação apenas altera status. Falta serialização de aprovações/capacidade,
   migração incremental e ocupação agregada. Reutilizar AlunoTurma.
3. **Rotas Públicas (#43 / frontend #30):** sem entidade/contratos de publicação,
   cidade/categoria/modalidade, paradas, catálogo e favoritos. #33/#34 e #39–#42
   complementam o mesmo módulo; evitar catálogos paralelos e publicação implícita.
4. **Rastreamento:** viagem/status/GPS privados básicos existem; faltam identidade
   da viagem na amostra, TTL, ordenação temporal e testes de reconexão. Posição
   própria do passageiro (#18 frontend) ainda não tem contrato seguro no backend.
5. **Excursões (#28–#32):** sem modelo/endpoints próprios no backend examinado.
   Criar entidade separada e convite seguro. Issues explicitam terceiro consumidor,
   `bevans-webapp#14`; não duplicar inscrição web dentro do app mobile.
6. **Segurança:** RESPONSAVEL (#26), ADMIN (#37) e GESTOR (#38) não existem.
   Não confundir segurança atual de motorista/passageiro com esses escopos.

OR-Tools já usa origem/fim explícitos e há testes de ida/volta/isolamento. Não há
cache ativo de otimização. Não substituir algoritmo nem implementar invalidação
de um cache inexistente (histórico da #3). Não existem migrações versionadas na
base inicial; Hibernate usa ddl-auto=update. Mudanças futuras de schema exigem
script incremental e validação PostgreSQL isolada antes de implantação.

Frontend local usa Expo **57**, React Native 0.86 e TypeScript 6, divergindo do
SDK 56 citado no prompt. Versões preservadas; não foi feita migração de SDK.
README backend diz Java 17+, mas pom exige Java **21**.

## Primeiro lote: rascunhos e pontos confirmados (#44, frontend #10/#25/#26)

- POST /turmas aceita nome/turno/motorista sem percurso; gera novo ID. Origem e
  destino podem ser salvos separadamente por PUT. Não usar rascunho como viagem:
  otimização continua validando ambos os pontos antes de calcular.
- Nome de origem/destino sem par de coordenadas confirmado recebe 400; não há
  geocodificação automática por nome nesse fluxo. PUT com novo pin e mesmo nome
  persiste o novo par. Atualização é transacional.
- POST /enderecos exige coordenadas finitas, dentro dos limites e diferentes de
  (0,0); proprietário vem da URL autorizada e ID do corpo é descartado. Número
  continua opcional. Edição passa a persistir complemento.
- Frontend compartilha um único campo de pesquisa entre motorista/passageiro.
  Rua/região/POI aproximados centralizam mapa mas não habilitam salvamento sem
  escolha/confirmacão explícita do pin. Toque marca ponto e arraste reposiciona.
  GPS inicial nunca vira endereço automaticamente. Limpar busca invalida seleção.
  Debounce, timeout, cancelamento e mensagens de erro existentes preservados.

## Verificações e limites

- Baseline frontend: `npm.cmd run check`, arquitetura + TypeScript + **58 testes**.
- Wrapper Maven falhou antes dos testes no sandbox. Maven instalado no cache foi
  usado offline com `-Dmaven.repo.local=...`; ambiente tem JDK 26 e Mockito exige
  `-Dnet.bytebuddy.experimental=true`. Não mudar stack do projeto por isso.
- Primeira execução Java sem esse parâmetro falhou por Byte Buddy e leitura de
  bibliotecas nativas no sandbox. Não é evidência de falha funcional dos fluxos.
- Teste contextLoads foi isolado explicitamente em H2/Firebase simulado para não
  depender de .env/credenciais de produção. JPA integration usa H2 descartável;
  testes HTTP usam autenticação Firebase simulada. OR-Tools nativo é executado.
- Primeiro lote backend: **91 testes** na suíte completa, incluindo persistência
  do rascunho em H2. Um fixture inicialmente omitia email/nome/telefone obrigatórios;
  corrigido e cenário de persistência passou.
- Frontend após primeiro lote: **65 testes passaram**; TypeScript/arquitetura
  passaram. Novos testes simulam seleção, toque e drag; não executam Mapbox nativo.
- Não houve teste Android/iOS, dois dispositivos, provedor real de endereços,
  PostgreSQL ou ponta a ponta. Não fechar #25/#44 com base apenas nessa bateria.

## Segundo lote: capacidade por turma (#35 / frontend #20)

Implementados capacidade nullable para legado, valor positivo obrigatório no
cadastro novo, edição do motorista, contagem de aprovados e vagas, proteção
transacional da última vaga/redução e deduplicação de pedidos concorrentes por
código. Frontend tem campos no cadastro/edição e confirma valor salvo na API.
Script incremental sem backfill arbitrário em docs/migrations; contrato e
recuperação em capacidade-turmas.md. Backend: **104 testes passaram**; frontend:
**68 testes**, arquitetura e TypeScript passaram. Migração smoke-testada apenas
em H2 PostgreSQL mode; PostgreSQL real e dispositivo seguem pendentes.

## Próximas etapas

Validar cadastro/mapa em dispositivo isolado. Confirmação de retorno do PUT e
reabertura do mapa implementadas: serviço compara ID/coordenadas, lê novamente
a lista autorizada quando PUT retorna corpo vazio, e tela carrega ponto salvo
com estado de erro/tentar novamente. Capacidade implementada conforme segundo lote;
completar cancelamento próprio de solicitação e validar PostgreSQL/dispositivo. Depois #33/#39
(publicação/modos/paradas), #34/#40/#42 (descoberta/embarques/favoritos), #41
(viagem/GPS) e excursões #29/#30/#31/#32. Cada etapa deve ter commit e atualização
objetiva das issues, sem fechá-las por cobertura parcial.
