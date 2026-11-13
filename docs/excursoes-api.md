# Excursões — estrutura backend #29

Entidade `Excursao` e tabela `excursoes` são independentes de Turma,
RotaPublica, Viagem escolar e convites escolares. Todos os endpoints atuais
exigem token Firebase e perfil MOTORISTA; apenas o organizador acessa os dados.
Publicada ainda não significa disponível sem autenticação: o contrato público
por convite seguro pertence à #30 e permanece pendente.

`POST /excursoes` cria RASCUNHO. `GET /excursoes?pagina=0&tamanho=20` lista
somente excursões próprias (máximo 50 por página). `GET /excursoes/{id}` consulta.
`PUT /excursoes/{id}` edita dados completos antes da partida; `versao` atual é
obrigatória para evitar sobrescrever edição feita em outro dispositivo.

```json
{"titulo":"Evento","destinoNome":"Centro de eventos",
 "destinoLatitude":-8.1,"destinoLongitude":-35.1,
 "origemNome":null,"origemLatitude":null,"origemLongitude":null,
 "partidaEm":"2026-11-25T10:00:00Z","retornoEm":null,"capacidade":12,"versao":0}
```

Partida futura, retorno opcional posterior, capacidade inteira positiva e
coordenadas válidas. Origem é opcional; quando informada exige nome e ambas
coordenadas. Destino sempre obrigatório. Instantes ISO-8601 com offset/UTC.

`PUT /excursoes/{id}/publicacao` com `{"versao":0}` muda RASCUNHO para PUBLICADA
antes da partida. `PUT /excursoes/{id}/cancelamento` cancela logicamente e é
idempotente. Não remove dados nem reabre cancelada/concluída. Enum CONCLUIDA
reservado ao ciclo operacional a completar com #31; não há conclusão automática.

Migração incremental 008 cria tabela/índice sem alterar dados dos outros
módulos. Recuperação mantém tabela e desabilita endpoints. A capacidade ainda
não reserva vagas: inscrições, concorrência de reservas, redução abaixo da
ocupação e tokens independentes serão implementados na #30 antes de habilitar
cadastro de passageiros. OR-Tools/check-in pertencem à #31. #32 depende de
integração nos repositórios mobile/web, fora do escopo de escrita desta sessão.

Testes de criação/edição/publicação/cancelamento, isolamento entre organizadores,
perfil passageiro, coordenadas/datas, versão obsoleta e preservação dos outros
módulos são executados em H2 e PostgreSQL isolado. Nenhuma interface foi criada.
