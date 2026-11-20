# Preparação para implantação controlada

Revisão local de 14/11/2026. Nenhum acesso à Oracle Cloud ou ao PostgreSQL de
produção foi realizado. A aplicação permanece na arquitetura Spring Boot,
PostgreSQL, Firebase, OR-Tools, Nginx e PM2 existentes. Este documento não autoriza
deploy, alteração de credenciais, execução SQL ou exclusão remota de contas.

## Inventário e migrações

O inventário **efetivamente aplicado em produção é desconhecido**. Antes de
qualquer implantação, o operador autorizado deve comparar schema, índices e
constraints com os scripts versionados e registrar hashes, data e resultado.
Não inferir aplicação de migrações a partir do número do release ou do Hibernate.

| Ordem | Incremento |
|---|---|
| 001–004 | Capacidade, publicação de rotas, embarques e índices do catálogo |
| 005–008 | Favoritos, pontos excepcionais, GPS e excursões |
| 009–012 | Coleta, convites, inscrições e execuções de excursões |
| 013–016 | Coleta privada, operação comum, notificações e GPS de excursões |
| 017 | Vínculos, convites e histórico de responsabilidade |
| 018 | Contato comercial consentido do motorista |
| 019 | Unicidade de UID Firebase e preflight de duplicidades |
| 020 | Fila transacional de exclusão de contas |
| 021 | Snapshot operacional do roteiro completo |
| 022 | Modalidade explícita das viagens |

Scripts em [migrations](migrations); 001–016 não foram reescritos nesta rodada.
Verificar também o índice legado em `scripts/sql/001_viagem_ativa_unica.sql`.
Aplicar somente os incrementos ainda ausentes, em ordem, após ensaio sobre cópia
isolada. Os testes de upgrade partem do schema histórico versionado, executam os
scripts e iniciam o Hibernate em `validate`; isso não substitui o inventário real.

Antes: backup consistente do banco, papéis e configurações; restauração ensaiada
em ambiente isolado; registrar release anterior, checksums e janela operacional.
Usar credenciais de migração separadas da aplicação quando disponíveis.
`HIBERNATE_DDL_AUTO=validate` é o padrão. **Não usar `update` como migração**.
Se houver divergência, interromper a inicialização e reconciliar o schema.

A migração 019 interrompe atomicamente diante de UIDs duplicados, preservando
IDs e linhas. A reconciliação requer identidade comprovada; não escolher uma
conta arbitrariamente. 017 não cria consentimento nem ativa vínculos históricos.
020 não apaga contas nem chama Firebase. A exclusão com relacionamentos retorna
409, preservando histórico; a política de retenção ainda precisa ser definida.

Recuperação: interromper escritores e avaliar falha antes de repetir scripts;
preferir correção incremental. Não executar DROP de tabelas para voltar um release.
Após uso de 017–020, preservar consentimentos, auditoria, contatos e tombstones de
UID. Um binário antigo que ignore a reserva de UID pode reabrir uma identidade
pendente de exclusão: não fazer rollback dessa proteção sem plano específico.
Restauração do backup é contingência com aprovação, janela e avaliação dos dados
criados desde o backup; ensaiar previamente e validar relacionamentos e contagens.

## Configuração

| Configuração | Requisito |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | PostgreSQL do ambiente; testes nunca apontam para produção |
| `HIBERNATE_DDL_AUTO` | `validate` |
| `app.firebase.enabled`, `app.firebase.credentials-path` | Firebase habilitado com arquivo externo protegido, fora do Git e do jar |
| `MOTORISTA_CADASTRO_CODIGO` | Segredo de cadastro do servidor; não incorporar no cliente |
| CORS | Conferir origens permitidas em SecurityConfig para os clientes autorizados |
| `app.time-zone` | Padrão America/Sao_Paulo; manter relógio/NTP correto |
| `OPENAPI_ENABLED` | `false` por padrão; dev permite acesso local, fora de dev exige autenticação |
| `app.notifications.worker.enabled` | `false` até configuração e autorização do consumidor |
| `app.push.enabled` | `false` até validação FCM autorizada |
| `app.contas.exclusao-worker.enabled` | `false`; ativação implica processamento de tarefas de contas |
| `app.contas.exclusao-remota.enabled` | `false`; ativação permite exclusão Firebase e exige autorização explícita |

Confirmar projeto Firebase, IAM mínimo, API FCM, registro/rotação/revogação de
dispositivos e consentimento. Ensaiar Android/iOS com contas de teste, múltiplos
dispositivos, preferência desabilitada e revogação. Aceite pelo provedor não é
prova de entrega no aparelho. Nenhum envio FCM real foi feito nesta rodada.
Não habilitar as duas flags de exclusão para testar o deploy. O verificador de
responsabilidade padrão nega ativação: definir processo confiável de identidade,
responsabilidade legal e consentimento antes de liberar rastreamento a responsáveis.

## SSE, GPS e Nginx

Nos endpoints `GET /viagens/{id}/localizacao/eventos`,
`GET /execucoes-excursao/{id}/localizacao/eventos` e
`GET /publico/excursoes/operacao/{id}/localizacao/eventos`, preservar streaming,
HTTP/1.1, `proxy_buffering off`, `proxy_cache off` e timeout de leitura superior
à duração máxima de canal (sugestão 330 s). Preparar a alteração de Nginx para
revisão; nenhuma configuração da VM foi editada. Não registrar Authorization,
X-Inscricao ou tokens em URL. Manter HTTPS.

O canal é limitado a cinco minutos ou à expiração da credencial, com revalidação
de acesso. O cliente deve renovar/reconectar com backoff, interromper ao sair e
não abrir polling simultâneo. Alternativa de polling: 10 s. GPS expira após 30 s;
retry não renova a idade. Localização expirada/finalizada não deve mostrar ponto
como atual. A coleta em segundo plano e permissões continuam sob responsabilidade
do aplicativo e exigem ensaio em dispositivos reais.

## Saúde, logs e monitoramento

`/health` é liveness; não comprova disponibilidade de PostgreSQL, Firebase ou
carregamento nativo do OR-Tools. Em homologação, verificar conexão/migrações,
validação Hibernate, autenticação, autorização, execução real de otimização e
consumo de memória na arquitetura de CPU da VM antes do deploy autorizado.

Erros inesperados retornam `X-Correlation-Id`, registrado com o tipo da exceção,
sem mensagem ou stack potencialmente privados. SQL bind/extract e o logger de
erros SQL com valores de constraints ficam desabilitados. Não habilitar DEBUG,
logs de corpos/headers ou captura de JWT, FCM, telefone e coordenadas em produção.
Revisar também access logs e ferramentas externas; a aplicação não controla sua
retenção. Monitorar latência/5xx, 401 versus indisponibilidade 503 do Firebase,
pool/locks do PostgreSQL, tamanho/idade da outbox, falhas finais, leases expirados
e canais SSE. Alertas devem usar IDs técnicos e métricas, sem payload pessoal.

## Antes do próximo deploy

1. Revisar commits e contratos novos com os clientes, especialmente perfil próprio
   versus perfil aninhado minimizado e DELETE de conta com resposta 202.
2. Resolver decisões de retenção e responsabilidade ou manter recursos bloqueados.
3. Obter inventário autorizado, backup e restauração ensaiada; validar todos os
   incrementos faltantes numa cópia isolada e executar as duas suítes Java 21.
4. Validar OR-Tools, Firebase/FCM e SSE em homologação sem credenciais de produção.
5. Revisar configuração, plano de recuperação, responsáveis e janela; obter
   autorização explícita para push/deploy/migrações/ativação remota.
6. Após eventual implantação autorizada, fazer smoke test com contas destinadas
   ao ensaio, incluindo negativa de IDOR e ausência de GPS/telefone no catálogo.

Não há comprovação de prontidão de produção enquanto essas etapas externas
estiverem pendentes. O resultado local não equivale a validação de frontend,
webapp, aparelhos, Nginx ou Oracle Cloud.


## Permissões PostgreSQL após migrações (#57)

Depois de cada migração SQL autorizada, aplicar os privilégios
necessários antes de liberar a nova versão da API.

### Procedimento

1. Confirmar o banco, host, porta e usuário administrativo.
2. Fazer backup e aplicar somente as migrações pendentes.
3. Executar o script de permissões.
4. Validar o acesso da aplicação antes de liberar a API.

### Configuração

Definir as variáveis de ambiente:

- PGHOST: host PostgreSQL autorizado.
- PGPORT: porta PostgreSQL.
- PGDATABASE: banco de destino.
- PGUSER: usuário administrativo.
- BEVANS_CONFIRMAR_DB: confirmação explícita do banco.

Comando de aplicação:

`bash scripts/aplicar-permissoes-pos-migracao.sh --apply vanapp_user postgres public`

O script exige confirmação do banco e executa os GRANTs
em uma transação PostgreSQL.

O arquivo scripts/sql/grant-app-permissions.sql concede
SELECT, INSERT, UPDATE e DELETE nas tabelas existentes,
USAGE e SELECT nas sequências e privilégios padrão
para novos objetos criados pela role responsável.

### Validação automatizada

O GitHub Actions executa scripts/test-db-permissions.sh
em PostgreSQL 16 isolado.

O teste verifica:

- Bloqueio da execução sem confirmação.
- Reaplicação dos privilégios.
- Leitura, inserção, atualização e exclusão.
- Acesso às sequências.
- Privilégios de tabelas e sequências futuras.

### Segurança operacional

Não executar migrações ou GRANTs em produção durante testes.

A confirmação do banco não substitui autorização, backup,
revisão do destino e planejamento de rollback.

O procedimento de permissões não executa migrações
automaticamente.

## Rate limit público com proxy confiável (#54)

`/publico/excursoes/**` mantém teto global de 600 requisições/min por
instância. Operações padrão têm 60/min por origem; consultas GET operacionais
em `/inscricao/**` e `/operacao/**` têm quota independente de 120/min,
para que polling legítimo não esgote a quota de inscrições. Esses limites
são por origem e instância, não por credencial; compartilhamento NAT e
múltiplas instâncias exigem ensaio de carga antes da liberação web pública.

Por padrão, o filtro usa exclusivamente o IP da conexão e ignora
`X-Real-IP`/`X-Forwarded-For`. Para identificar visitantes distintos atrás
do Nginx, é possível configurar `app.proxy.trusted-ips` com IPs literais
exatos dos proxies auditados. Só aceita `X-Real-IP` com um único IPv4/IPv6
literal válido quando a conexão chega diretamente de um IP dessa lista.
Cabeçalho ausente, duplicado ou inválido recai no IP da conexão.
`X-Forwarded-For` nunca é aceito pelo limitador.

**Não ativar a confiança em produção sem revisar Nginx e rede.**
Pré-requisitos: Nginx deve sobrescrever o cabeçalho com
`proxy_set_header X-Real-IP $remote_addr;` sem encaminhar valores
fornecidos pelo visitante; backend interno deve ser inacessível
externamente; conferir CDN/real_ip, cadeias de proxies, endereços IPv4/IPv6
reais e eventuais transformações de `getRemoteAddr()` do framework.
Confiar em localhost quando outros processos locais podem chamar diretamente
a API permite falsificação de IP pelo localhost.

Ensaiar HTTPS em homologação isolada, com dois clientes de redes diferentes,
cotas separadas, IP/header falsificado, `429`/`Retry-After`, polling
e teto global. Revisar também o consumo de SSE e limites de conexões.
A alteração não muda Nginx, firewall ou configuração em execução.
