# Favoritos — backend #42

- PUT /rotas-publicas/{id}/favorito: favoritar, 204 idempotente.
- DELETE /rotas-publicas/{id}/favorito: remover logicamente, 204 idempotente,
  inclusive após despublicação ou quando não há favorito próprio.
- GET /rotas-publicas/favoritas?pagina=0&tamanho=20: somente favoritos do
  passageiro autenticado, com a mesma paginação/ordem por ID do catálogo.
- GET /rotas-publicas/catalogo?somenteFavoritas=true aceita os demais filtros
  combinados. Catálogo e detalhes GET /rotas-publicas/{id} incluem favorita
  calculada para a identidade atual, nunca para usuarioId enviado pelo cliente.

Alterações/listagem exigem PASSAGEIRO. Unique(usuario_id,rota_id), lock do usuário
e transação impedem duplicatas concorrentes. Remover e favoritar de novo reutiliza
o registro. Preferência fica no PostgreSQL e não depende de memória do processo.

Somente rota publicada/ativa/não excluída pode receber novo favorito. Após retirar
publicação ou desativar rota, a preferência é preservada, mas não aparece na lista
nem permite consultar dados privados. Se o dono republicar/ativar explicitamente,
o favorito volta a aparecer. Titular pode removê-lo mesmo durante indisponibilidade.

Sem exclusão física no fluxo. Migração incremental 005-favoritos.sql preserva
dados existentes e adiciona tabela, unicidade/FKs e índice por titular. Recuperação
mantém dados ao voltar aplicação; não usar DROP como rollback.

Testes em H2 e PostgreSQL cobrem persistência entre consultas, idempotência,
remoção/reativação, duas contas, flags, publicação/disponibilidade, paginação e
concorrência. Fluxo HTTP real cobre autenticação, usuário arbitrário ignorado,
papel de motorista bloqueado e flags/listas diferentes por passageiro.
Implementação exclusivamente backend; frontend não foi alterado.
