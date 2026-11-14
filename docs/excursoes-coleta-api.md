# Coleta de Excursões — #29

`PUT /excursoes/{id}/coleta`, Bearer Firebase do organizador. O serviço também
valida titularidade; outro motorista não pode alterar a excursão. Body:

```json
{"modo":"MULTIPLAS_PARADAS","versao":0,"paradas":[
 {"id":null,"nome":"Praça A","latitude":-8.1,"longitude":-35.1,
  "horario":"08:00","instrucoes":"Portão principal","pontoConfirmado":true},
 {"id":null,"nome":"Praça B","latitude":-8.2,"longitude":-35.2,
  "horario":null,"instrucoes":null,"pontoConfirmado":true}
]}
```

PORTA_A_PORTA exige lista vazia; PARADA_UNICA exatamente um ponto;
MULTIPLAS_PARADAS de dois a 50 pontos. Ordem do array é a ordem persistida.
Coordenadas finitas dentro dos limites, diferentes de (0,0), com confirmação
explícita do ponto. Não há geocodificação automática por nome. Horário é local
informativo da parada, sem inferir ETA ou janela de roteamento.

Reutilizar ID retornado para renomear, reposicionar ou reordenar. IDs duplicados
ou de outra excursão são recusados. Omitir parada a desativa, preservando ID/dados.
GET privado da excursão retorna modoColeta e paradas ativas ordenadas. Destino
final e origem operacional continuam campos separados; nenhuma parada altera
o destino automaticamente. Não há publicação em catálogo de Rotas Públicas.

Atualização exige a versão atual de Excursao, inclusive quando só mudam paradas.
Lock pessimista serializa edições; versão obsoleta retorna 409. Cancelada,
concluída ou com partida já ocorrida não aceita alterações. Todas as excursões
existentes recebem PORTA_A_PORTA na migração 009, preservando os campos anteriores.
Scripts 001–008 não foram alterados. Aplicar 009 uma vez com registro de execução,
antes de iniciar a aplicação que usa Hibernate validate. Rollback da aplicação
mantém tabelas e dados; não executar DROP.

O incremento 011 da #30 bloqueia mudanças de modo, paradas/horários/endereço
quando há reserva ativa e impede capacidade abaixo da ocupação. Consulte
`excursoes-inscricoes-api.md`. Planejamento/OR-Tools e execução persistida estão
em excursoes-roteiro-api.md/excursoes-execucao-api.md. Controle físico continua #46.
Não interpretar definição de coleta como inscrição ou embarque confirmado.
