# Coleta privada por turma e sentido — #48

Configuração independente em `/turmas/{id}/coleta/{sentido}`, sentido IDA/VOLTA.
Ausência de configuração mantém PORTA_A_PORTA, sem backfill ou alteração dos
endereços existentes. Entidades próprias: ColetaPrivada, ParadaPrivada,
EscolhaParadaPrivada; nenhuma publicação no catálogo. Turma com configuração
de RotaPublica não utiliza este contrato, mesmo se temporariamente despublicada.

`PUT` somente pelo motorista responsável:

```json
{"modo":"PARADAS_DEFINIDAS","versao":0,"paradas":[
 {"id":null,"nome":"Praça","latitude":-8.1,"longitude":-35.1,
  "horario":"07:00","pontoConfirmado":true}
]}
```

PORTA_A_PORTA exige array vazio, PARADAS_DEFINIDAS de 1 a 50 pontos.
Versão inicial 0; cada edição incrementa revisão, inclusive só de paradas.
Array determina ordem; ID omitido cria, ID existente renomeia/reposiciona,
omitido da lista desativa logicamente. IDs alheios/duplicados rejeitados.
PIN obrigatório, limites geográficos e (0,0) validados. Não geocodifica por nome.
Horário local informativo; não é ETA. Revisão obsoleta retorna 409.

`GET ?enderecoId=...` retorna modo, versão, paradas ordenadas, escolhidaId,
sugestão e viagemId/GPS. Motorista proprietário e passageiros APROVADOS consultam
paradas; só o próprio passageiro usa seu enderecoId para sugestão. Nenhum endereço
residencial é retornado. Haversine em metros/linha reta, desempate por ID, sem
afirmar distância/tempo caminhável. Calculada a cada consulta: mudanças no endereço
ou paradas não substituem a escolha. Sem pontos/endereço, sugestão é nula.

Escolha própria: `PUT /minha-parada`, body `{"paradaId":1,"versao":1}`;
`DELETE /minha-parada` remove logicamente. Dono pode atribuir/remover explicitamente
em `/passageiros/{alunoId}/parada`. Passageiro não altera outro aluno; só aprovados
recebem atribuição. Uma escolha por turma/sentido/aluno, preservada no banco.
Troca de modo com escolhas ativas ou remoção/mudança de coordenadas/horário de
parada usada retorna 409; remover/reconciliar escolhas primeiro. Mudanças de
ordem/nome também respeitam presenças planejadas. Configuração não apaga Endereco.

Segurança de planos: viagem ativa da turma bloqueia configuração/escolhas;
presenças atuais/futuras com intenção de viajar bloqueiam alterações até ajuste
explícito. NAO_VOU/LIMPAR não bloqueiam. Regra conservadora vale para ambos os
sentidos, evitando invalidação silenciosa. Confirmação por `/rota/confirmar`
exige escolha quando o sentido usa paradas e dispensa endereço residencial;
endereços de ida/volta anteriores são preservados. Em PORTA_A_PORTA continuam
obrigatórios e separados por sentido. Início valida escolhas dos esperados.

`POST /planejamento?otimizar=false`, somente dono, retorna extremos reais e
paradas com passageiros esperados do dia; usa a escolha, nunca a residência.
IDA segue ordem manual; VOLTA inverte paradas e extremos. Otimização diferente
só com `otimizar=true`, sem gravar nova ordem. No modo porta a porta, reutiliza
o OR-Tools existente. O endpoint legado de otimização usa escolhas/ordem manual
quando configurado por paradas, sem introduzir pontos residenciais nessa rota.

Durante viagem, GET mantém paradas e escolha, liga viagem ativa do sentido e
consulta PosicaoViagemService existente. SEM_LOCALIZACAO/DESATUALIZADA não inventam
GPS; posição expirada vem sem coordenadas. Sem viagem, viagemId/gps nulos.
Não existe canal paralelo. Ordem manual é a referência de partida; prévia
otimizada não altera a execução silenciosamente.

Migração 013 após 012, sem alterar scripts anteriores. Aplicação continua
Hibernate validate. Reversão de aplicação mantém tabelas, IDs e endereços.
