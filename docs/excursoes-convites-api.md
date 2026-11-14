# Convites seguros de Excursões — etapa da #30

O organizador autenticado cria convites de excursão PUBLICADA antes da partida:
`POST /excursoes/{id}/convites` com `{"expiraEm":"2030-01-01T18:00:00Z"}`.
Validade futura, até a partida e no máximo 30 dias. Limite de 100 emissões por
excursão, inclusive revogadas. Resposta 201/no-store com metadados e `token`.
Token de 256 bits gerado por SecureRandom; apenas SHA-256 é persistido.
Não é possível recuperar token por listagem. Guardá-lo no consumidor apenas para
compartilhamento autorizado. Perdeu o token: revogar e emitir outro.

`GET /excursoes/{id}/convites` lista metadados sem token/hash;
`DELETE /excursoes/{id}/convites/{conviteId}` revoga idempotentemente (204).
Perfil MOTORISTA e titularidade conferidos no servidor; ID de outro convite
ou excursão não autoriza ação. Convites não usam códigos de turmas privadas.

Consulta sem login: `GET /publico/excursoes/convite`, header `X-Convite`.
Somente esse método/caminho é público. Resposta inclui título, destino, data,
modo e paradas autorizadas; não inclui organizador/telefone, origem da garagem,
participantes, pins residenciais, inscrição individual ou GPS. Não aceita token
via query/path. Convite inválido, expirado, revogado ou excursão indisponível
retorna o mesmo 404. Cancelar excursão ou passar a partida invalida a consulta.

O frontend/webapp deve construir o link de sua página com token em fragmento
local e enviá-lo ao backend no header. Este backend não inventa domínio web nem
cria página/JavaScript. Não incluir tokens em URLs da API, logs, analytics ou
referrer. Respostas usam Cache-Control: no-store e Referrer-Policy: no-referrer.

Proteção nesta VM: até 60 requisições/minuto por IP direto e 600 globais para
`/publico/excursoes/`, inclusive tentativas inválidas. 429 com Retry-After 60.
Mapas de contagem reiniciam por janela e não guardam tokens. X-Forwarded-For do
cliente é ignorado: atrás de Nginx, IPs podem compartilhar limite. Configuração
de proxy confiável/rate limit de borda requer futura revisão autorizada; nenhum
proxy ou produção foi alterado. Estado de limite em memória não é distribuído.

Aplicar migração incremental 010 uma vez após 009, com rastreabilidade. Scripts
antigos preservados. Hibernate continua validate por padrão; não usar update
para substituir constraints/índices. Rollback da aplicação preserva dados.

Esta etapa entrega convites e consulta, não reserva vagas. Cadastro, credencial
individual, controle concorrente de capacidade e confirmação pertencem ao
incremento seguinte da #30. Link de convite não concede acesso administrativo
ou localização privada. Não declarar o fluxo de inscrição concluído ainda.
