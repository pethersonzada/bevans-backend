# Alertas de proximidade e resumos operacionais

Incremento da #47/#41, compartilhado por turmas privadas, rotas públicas FIXA/FLEXÍVEL e excursões. Nenhuma nova migração: usa operação/snapshot da 014, outbox/central da 015 e GPS da 007/016.

Cada nova amostra GPS válida pode criar um evento na outbox, sob o mesmo lock e transação da origem. Repetir uma amostra não publica novamente. Não há outro canal GPS, consultas a provedores de rota ou polling adicional. O consumidor da central continua exigindo ativação explícita.

São considerados apenas registros `AGUARDANDO`, com vínculo autorizado, na menor ordem operacional ainda pendente. Passageiros atribuídos à mesma ordem podem ser avisados juntos. O ponto vem do snapshot operacional: residência confirmada em porta a porta, parada efetivamente escolhida na coleta privada, parada aprovada/ponto excepcional na rota pública e ponto da inscrição na excursão. A sugestão de parada mais próxima não interfere.

GPS deve estar atualizado (TTL de 30 segundos) e ter precisão de até 50 metros. Distância Haversine em linha reta somada à precisão determina o evento mais específico:

| Limite conservador | Evento |
|---|---|
| Até 500 m | `VAN_APROXIMANDO` |
| Até 200 m | `VOCE_E_PROXIMO` |
| Até 50 m | `VAN_CHEGOU` |

Não se calcula tempo estimado de chegada, trajeto caminhável nem prova física de chegada. A mensagem de chegada descreve uma posição recente junto ao ponto; embarque/desembarque continuam exigindo ação do passageiro ou motorista.

Uma chave por operação/registro/tipo limita cada aviso a uma ocorrência no sentido daquela execução. Se a fila atrasar mais de 30 segundos, o evento é descartado. Antes de persistir o aviso e reservar o push, são revalidados GPS, estado ativo, ordem, vínculo, precisão e faixa de distância; mudança de contexto invalida o aviso. Não há replanejamento implícito de execução iniciada: o snapshot permanece a fonte operacional. Avisos que já estão na central são histórico com data de criação; o aplicativo deve consultar o GPS/estado atual para acompanhamento ao vivo.

Motorista recebe `PASSAGEIROS_AGUARDANDO` no início quando aplicável e `TODOS_EMBARCARAM` uma vez quando todos os esperados têm estado EMBARCADO ou DESEMBARCADO. Não se infere presença pelo GPS. Ausência explícita e confirmações feitas por passageiros continuam usando os eventos operacionais existentes, sem notificar o motorista por cada alerta de proximidade de passageiros.

Mensagens push permanecem genéricas, sem endereços, nomes ou coordenadas. Participante sem aplicativo recebe o aviso no feed individual, sem dispositivo Firebase presumido. Validação local não substitui teste real de notificações e localização em segundo plano nos aplicativos.
