# Rotas Públicas urbanas — #33/#39 e ligação com #35

Entidade RotaPublica separada de Turma e de Excursões. Referência explícita a
uma turma reaproveita autorização/vínculos/capacidade. Configurar publicação não
publica a turma nem copia garagem/endereço privado: origem e destino públicos
devem ser informados expressamente no mapa. Rota nasce não publicada.

Todos os endpoints exigem Firebase. DTOs não contêm passageiros, telefone, perfil
completo do motorista ou GPS. Cidade representa cidade de operação urbana.

| Método | Caminho | Contrato |
| --- | --- | --- |
| POST | /rotas-publicas | Dono cria configuração, com turmaId, nome, cidade, categoria, modo, origem/destino em pares, dias MONDAY..SUNDAY, horárioIda/horárioVolta (HH:mm), descrição/região/campus opcionais |
| GET | /rotas-publicas/minhas | Configurações do motorista autenticado |
| GET | /rotas-publicas/{id} | Rota publicada/ativa ou dono; detalhes permitidos e paradas ordenadas |
| PUT | /rotas-publicas/{id} | Dono altera dados completos, sem trocar turmaId |
| PUT | /rotas-publicas/{id}/publicacao | Body {valor:true/false}; publicar exige capacidade e rota ativa |
| PUT | /rotas-publicas/{id}/disponibilidade | Body {valor:true/false}; desativar também despublica |
| PUT | /rotas-publicas/{id}/paradas | Array na ordem desejada; {id?,nome,latitude,longitude,horario?}; sem id cria; omitida fica inativa; ID alheio/duplicado rejeitado |
| POST | /rotas-publicas/{id}/planejamento?sentido=IDA&otimizar=true | Dono obtém prévia; FIXA mantém ordem, VOLTA inverte. FLEXIVEL usa OR-Tools com extremos explícitos, sem persistir ordenação automaticamente |
| POST | /rotas-publicas/{id}/solicitacoes | Passageiro autenticado pede vaga sem convite privado; continua PENDENTE até aprovação |

Estado e paradas persistem. Sem hard delete de paradas, preservando IDs.
Quantidade máxima configurável por app.rotas-publicas.limite-paradas (padrão 50).
Viagem ativa impede alterações e replanejamento; iniciar viagem passa a usar o
mesmo lock da turma. Pending pedidos nunca entram na lista de paradas.

Migração incremental 002-rotas-publicas.sql cria somente tabelas adicionais,
com FK/índices/constraints e sem backfill/publicação. Recuperação preserva dados
e tabelas adicionais ao voltar aplicação; nenhum DROP em produção.

Validação local: 8 testes de integração de publicação, CRUD/ordem/persistência,
privacidade do ponto de origem, FIXA/VOLTA/FLEXIVEL com OR-Tools, titularidade,
viagem ativa e vínculo cancelado reutilizado. Mesmos 8 passaram no PostgreSQL 16
descartável. As migrações 001+002 também passaram em schema PostgreSQL novo,
com turma/vínculo legados e catálogo permanecendo vazio. Testes HTTP adicionais
cobrem identidade, permissão e sessão.

Ainda pendentes: frontend #26 e validação Android; fluxo específico de embarque
por parada/ponto solicitado (#40/#27); catálogo paginado/favoritos (#34/#42),
rastreamento (#41) e prova ponta a ponta. Não considerar épicos concluídos.
