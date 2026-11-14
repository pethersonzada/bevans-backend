# Planejamento privado de Excursões — incremento da #31

`POST /excursoes/{id}/planejamento?sentido=IDA&otimizar=false`, token Firebase
do organizador. Retorna lista ordenada de pontos com tipo, paradaId quando
aplicável, nome/coordenadas e participantes (inscricaoId/nome). Não inclui
contato ou credenciais; não é acessível por convite/credencial individual.
Resposta no-store; DTO oculta dados privados no toString.

Somente inscrições CONFIRMADAS daquela excursão compõem a coleta. Pendentes,
canceladas e expiradas ficam de fora. PORTA_A_PORTA usa snapshots dos pins
confirmados de cada participante. PARADA_UNICA agrupa confirmados no ponto
único. MULTIPLAS_PARADAS agrupa por parada ativa e preserva sua ordem relativa,
omitindo pontos sem inscritos confirmados da coleta operacional. O GET privado
da excursão continua mostrando todas as paradas cadastradas para edição.

IDA: origem operacional configurada, coletas e destino do evento no final.
Origem opcional ausente não é inventada: lista começa pela coleta, ou contém
apenas destino quando não há confirmados. Otimizar exige origem explícita.
VOLTA só é aceita com retornoEm e origem operacional configurados: parte do
destino do evento, percorre pontos em ordem inversa como DESEMBARQUE e termina
na origem operacional. Usa a mesma inscrição/ponto; ainda não há opção individual
de somente ida/somente volta. Não confundir o último ponto com destino da ida.

Somente `otimizar=true` aciona o OR-Tools existente, com extremos fixos e limite
de tempo já configurado. Prévia não altera ordem/IDs cadastrados, reservas ou
estado físico de embarque. O adaptador interno usa o DTO de coordenadas do
algoritmo existente; não cria nem consulta entidades ParadaPublica.

Consulta em transação repeatable-read mantém uma visão consistente de excursão,
paradas e confirmados. Excursão precisa estar PUBLICADA e antes do horário do
sentido pedido. Cancelada, concluída ou após a partida desse sentido retorna
409. IDA/VOLTA inválido ou volta/origem ausente recebe 400. Falha de otimização
retorna erro tratado pelo otimizador; não devolve sucesso com ordem inventada.

Início/encerramento com snapshot persistido foi acrescentado no incremento 012;
consultar `excursoes-execucao-api.md`. Prévia de sentido já executado recebe 409
e não altera o snapshot. Controle compartilhado de embarque/desembarque #46 e
notificações #47 continuam pendentes. Não há GPS público de Excursões. A prévia
em si utiliza inscrições/paradas persistidas por 009–011, sem tabela paralela.

Testes exercitam OR-Tools nativo nos três modos, exclusão de pendentes,
privacidade, isolamento entre motoristas, sequência fixa/volta, extremos e
ausência de mutação do cadastro, em H2 e PostgreSQL isolado.
