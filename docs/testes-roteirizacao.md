# Regressões de roteirização

Referências: issues #1, #2, #9 e #10 de `pethersonzada/bevans-backend`.

Execute com JDK 21 e Maven:

```powershell
mvn '-Dtest=RotaServiceTest,PresencaRotaIntegrationTest,*SegurancaTest,*HttpTest,AccessPolicyTest' test
```

O wrapper também pode ser utilizado quando configurado no ambiente. Com JDK 26,
a versão atual do Byte Buddy requer `-Dnet.bytebuddy.experimental=true`.

Os testes executam o OR-Tools real, incluindo suas bibliotecas nativas.
`PresencaRotaIntegrationTest` usa H2 em memória e executa o JPQL real e as
restrições de exclusão lógica do Hibernate. Não inicializa Firebase nem usa
PostgreSQL. O teste preexistente `VanappBackendApplicationTests`, que carrega
a aplicação completa e exige configuração externa, fica fora deste comando.

## Comportamento verificado

- A origem alternativa vem do motorista associado à turma. Duas turmas com
  motoristas em posições opostas produzem ordens distintas; ausência de
  motorista impede a otimização.
- Origem e destino configurados determinam a ordem dos passageiros em IDA e
  VOLTA. A resposta contém apenas passageiros; os extremos são configurados
  no OR-Tools e não fazem parte do DTO público. Zero e um passageiro são aceitos.
- Somente presenças do dia e da turma com vínculo aprovado participam.
  Pendentes, rejeitados, removidos e usuários vinculados somente a outra turma
  são excluídos, nos dois sentidos.
- Cada sentido usa seu endereço específico. Endereço ausente, coordenadas
  inválidas ou proprietário diferente/ausente excluem a parada. Não há escolha
  arbitrária de endereço nem substituição pelo outro sentido.
- O fallback legado só existe para presenças sem turma e com endereço explícito;
  a consulta atual por turma não seleciona esses registros.
- Não há `@Cacheable` na otimização atual: cada chamada consulta as presenças e
  recalcula a sequência. O teste com H2 altera o endereço, limpa o contexto de
  persistência e comprova a coordenada nova; também comprova a retirada do
  passageiro após revogação do vínculo.

H2 não substitui uma validação específica no PostgreSQL. Estes testes também
não cobrem implantação, dados históricos em produção ou integração visual mobile.
