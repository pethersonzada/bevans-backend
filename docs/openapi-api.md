# OpenAPI e Swagger

Issue #7. Evolui `SwaggerConfig` e a dependência springdoc 2.5.0 já existentes,
sem duplicar beans ou trocar a stack. Compatibilidade com o Spring Boot 3.3.0
do projeto verificada gerando documento real no teste de integração.

## Ativação segura

- Padrão: `springdoc.api-docs.enabled=false` e `springdoc.swagger-ui.enabled=false`.
- Desenvolvimento explícito: `--spring.profiles.active=dev`; acessar
  `/v3/api-docs` (JSON), `/v3/api-docs.yaml` (YAML), `/swagger-ui/index.html`.
- Fora de dev, `OPENAPI_ENABLED=true` habilita os recursos **exigindo Firebase**.
  Não torna a documentação pública. Swagger UI no navegador não injeta token na
  navegação inicial: preferir o ambiente dev isolado ou proxy autenticado aprovado.
- Perfil `prod` prevalece sobre `dev` para negar acesso anônimo à documentação,
  mesmo se os dois perfis forem ativados. Não habilitar dev em produção.
- Endpoints de negócio continuam autenticados mesmo com documentação dev pública.

Não colocar token real em exemplos, arquivos do repositório ou capturas. Em
desenvolvimento autorizado, usar o botão Authorize com ID token do ambiente de
testes. O esquema existente `firebaseAuth` foi preservado (Bearer/JWT).

## Cobertura e correspondência com execução

Documento gerado pelos controllers/DTOs reais: autenticação/perfil, turmas,
presenças, endereços, otimização privada, publicações/paradas, catálogo, favoritos,
embarques públicos, excursões, convites/inscrições, operação física, GPS/SSE,
central/dispositivos, contato e vínculos de responsáveis. Corpos Map legados
têm schemas explícitos em `ContratosEntrada`, sem alterar a execução dos corpos.

Schemes públicos de excursão:

- `Convite`: header X-Convite; consulta de divulgação e emissão de credencial.
- `Inscricao`: header X-Inscricao; estado próprio, controle físico, GPS,
  notificações e contato, sujeitos às permissões e validade do serviço.
- Criar inscrição exige **os dois headers juntos**; não é alternativa OR.
- Health não exige autenticação. Credencial de convite não concede GPS.

Os schemas gerados diferenciam perfil próprio de perfil público mínimo, entradas
e saídas de parada e auditoria de responsabilidade/operação; não se compartilha
um schema incorreto apenas porque duas classes internas se chamam `Evento` ou
`Parada`. Campos ignorados pelo Jackson não reaparecem na documentação pública.

Paginação documenta parâmetros base zero e tamanho 1–50. Algumas rotas retornam
`PaginaDTO` (`itens,total,pagina,tamanho,totalPaginas`), outras Page do Spring
(`content`, metadados de página); consumir o schema específico de cada operação.
Não supor que são intercambiáveis. Contratos Markdown detalham limites adicionais.

Respostas reais de convites 201 e favoritos/cancelamento de solicitação 204
estão anotadas; demais métodos preservam seus códigos reais. Erros comuns são
documentados no schema `ErroAPI`: timestamp ISO local, status numérico, erro e
caminho. Códigos aplicam-se conforme a operação; não implicam que toda operação
produza todos eles. Localização legada foi marcada deprecated/410.

SSE usa `text/event-stream`: evento localizacao, reconexão sugerida em 10 segundos,
encerramento/revalidação de sessão e limite de cinco minutos. O exemplo distingue
viagemId de execucaoId; polling e TTL são descritos no contrato de rastreamento.
Estado físico e regras de correção/versionamento acompanham as operações; GPS
nunca é confirmação de embarque/desembarque.

## Validação

`OpenApiIntegrationTest` sobe a aplicação em H2 de teste/Firebase desativado,
gera `/v3/api-docs`, verifica módulos, DTOs, headers AND, códigos 201/204, SSE,
paginação, perfis e referências sem destino; também carrega Swagger UI e comprova
que API privada segue 401 sem token. Testes HTTP separados comprovam negação da
documentação desabilitada e prioridade de prod sobre dev. Não há token real nem
teste com banco de produção. Os testes das APIs continuam sendo a evidência do
comportamento; um schema sozinho não prova integração frontend/mobile.
