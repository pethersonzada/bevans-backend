# Estado de retomada — 13/11/2026

## Bases e trabalho entregue

Branch local nos dois repositórios: `fix/address-draft-stabilization`.
Não houve push, deploy ou migração em produção.

Backend partiu de 365b930; frontend partiu de b149874. Ambos estavam limpos.
Não repetir a coleta completa das issues: snapshot inicial com 42 abertas,
detalhes/comentários e histórico fechado em backlog-2026-11-13.json. Consultar
atualizações desde a coleta e a issue nova backend #44.

Commits do primeiro lote:
- Backend 1bbff3a: contextLoads isolado.
- Backend ce2b9eb: rascunhos, coordenadas confirmadas, ID/proprietário, complemento.
- Backend 6634caa: auditoria e snapshot.
- Frontend 85d6968: campo único, pin manual/drag, reabertura e confirmação do PUT.

Capacidade implementada no lote seguinte, com modelos, API, lock transacional,
script incremental, cadastro/edição mobile e testes. Consultar git log para commits
finais deste lote; escopo/limites em capacidade-turmas.md. Endereços e capacidade
não concluem todo o projeto.

## Verificações

Frontend inicial: 58 testes; primeiro lote: 65; capacidade: 68. npm.cmd run check
inclui arquitetura, TypeScript e testes, todos passaram. Mocks de Mapbox não
substituem development build em dispositivo.

Backend primeiro lote: 91 testes; capacidade: **104 testes passaram**. Acrescenta testes unitários, HTTP,
transações concorrentes H2 e smoke do script em H2 PostgreSQL mode. Log final
local: target/capacidade-final.log. Não registrar PostgreSQL como validado.

Maven Wrapper falha neste sandbox; usar Maven do cache local, modo offline e
-Dmaven.repo.local apontando ao cache do usuário. JDK disponível é 26; para
Mockito atual usar -Dnet.bytebuddy.experimental=true. pom continua Java 21.
Cache/native OR-Tools exigiu execução local fora da restrição do sandbox.

## Issues e próximos passos

#44 backend criada; #44/#6 backend e #10/#25 frontend receberam comentários de
implementação/testes/commits. #35 backend/#20 frontend recebem progresso de
capacidade. Nenhuma issue foi fechada por implementação parcial.

1. Validar script/concorrência em PostgreSQL isolado. Docker CLI existe, daemon
   não está acessível. Não ligar teste à configuração de produção.
2. Validar Android/iOS: endereço/garagem/destino, reinício, IDA/VOLTA e assentos
   A=12/B=18, edição A=15, aprovação e erros. Sem dispositivo nesta execução.
3. Completar #35: cancelamento próprio/consulta de solicitação e ligação ao
   catálogo. Não prometer catálogo público porque capacidade privada existe.
4. #33/#39 backend: publicação explícita, cidade/categoria/FIXA/FLEXIVEL/paradas.
   Frontend #26, épicos backend #43/frontend #30. Depois catálogo #34, embarque
   #40, favoritos #42 e interfaces #19/#27/#29.
5. Rastreamento #41/foreground e reconexão #6/#28 frontend; identidade da viagem,
   TTL e privacidade; posição própria #18 exige DTO seguro, não liberar otimização.
6. Excursões #29/#30/#31/#32: entidade independente, convite, capacidade/check-in.
   Inscrição web pertence ao terceiro repositório bevans-webapp#14, não duplicar.

Escrita frontend fica fora da raiz liberada do sandbox: scripts temporários em
target prepararam arquivos e manifest com originais; cópia só ocorreu após
comparação para preservar mudanças concorrentes. Scripts temporários não são
necessários para executar o produto e não foram commitados.
