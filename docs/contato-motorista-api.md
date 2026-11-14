# Contato comercial protegido por WhatsApp

Issue #36. Migração 018 após 017; não copia telefone particular do perfil e não
habilita divulgação de nenhum motorista automaticamente. Não implementa chat,
não armazena conversas e não confirma entrega/atividade de conta no WhatsApp.

## Configuração do motorista

`GET /motorista/contato` e `PUT /motorista/contato` exigem Firebase/MOTORISTA;
o ID vem exclusivamente da sessão. Corpo de atualização:

```json
{"telefone":"+55 (81) 99999-8888","habilitado":true,"aceiteDivulgacao":true,"versao":0}
```

Número acima é exemplo sintético, não credencial de teste externo. Retorno:
`telefone` normalizado só com dígitos, `habilitado`, `consentimentoEm`, `versao`.
Versão zero representa ausência de configuração; criação retorna um e edições
incrementam. Versão obsoleta retorna 409. Repetição com versão atual sem alteração
não modifica consentimento. Habilitar exige aceite explícito; desabilitar permite
`{"habilitado":false,"aceiteDivulgacao":false,"versao":1}` sem reenviar telefone.
O número salvo pode ser mantido desabilitado, mas nunca é retornado a passageiros.

MVP brasileiro: DDI **55 obrigatório**, DDD cadastrado no validador, assinante fixo
com oito dígitos ou móvel iniciado em 9 com nove dígitos. Aceita separadores de
formatação e `+` inicial; rejeita URLs, letras, quebras de linha e outros DDIs.
Valida estrutura, não titularidade do número nem disponibilidade no WhatsApp.
Suporte internacional/atualização de numeração requer evolução explícita.

## Consulta autorizada

- `GET /turmas/{turmaId}/contato-motorista`: passageiro com matrícula APROVADO.
- `GET /rotas-publicas/{rotaId}/contato-motorista`: mesma regra na turma da rota.
- `GET /publico/excursoes/inscricao/contato-motorista`: X-Inscricao individual
  válido, inscrição CONFIRMADA e excursão PUBLICADA. Convite público não concede
  contato; cancelamento/expiração retira a permissão.

Decisão conservadora permitida pelo backlog: solicitação apenas PENDENTE não
libera contato; requer aprovação. Não se ampliou o catálogo para expor telefones.
Despublicar rota não desfaz matrícula: membros aprovados continuam autorizados
ao contato habilitado; opt-out explícito ou remoção da matrícula bloqueiam novas
consultas. Outra conta, vínculo inexistente/revogado ou divulgação desabilitada
recebem 404 genérico. Sem Firebase nos endpoints privados: 401; configuração por
passageiro: 403. Responsável não herda automaticamente essa permissão.

Resposta mínima: `motoristaId`, `nome`, `telefoneComercial`, `waMe`. Link gerado
exclusivamente como `https://wa.me/` + número validado. Sem texto pré-preenchido,
coordenadas, identificação de passageiros ou parâmetros vindos do cliente.
Não executar GET de wa.me como teste de entrega nem enviar mensagem automática.
Revogar não apaga um contato já visto/salvo no aparelho; vale para próximas
consultas, não para apagar informação fora do BeVans.

## Correção de privacidade dos contratos antigos

Foi reproduzida serialização de telefone/email/UID/endereço/coordenadas privados
por `Usuario` aninhado em turma. Relações e `/usuarios/motorista` agora expõem só
perfil mínimo (id/nome/tipo), impedindo contornar o opt-in por caminhos antigos.
Isso também evita revelar dados particulares de passageiros por serialização
genérica de relacionamentos. Contratos operacionais específicos autorizados
continuam com os campos necessários ao motorista.

O **perfil próprio** mantém os campos anteriores via `PerfilUsuarioDTO` em
`/usuarios/por-uid/{uid}` e respostas próprias de cadastro/edição. Outro UID é
negado. Frontend deve buscar contato comercial pelo endpoint protegido, sem
depender de campos particulares aninhados; nenhuma tela foi alterada aqui.

## Migração e recuperação

018 cria tabela separada com FK, versão, opt-in/timestamp e constraint de telefone;
não publica números existentes. Validar em cópia isolada com Hibernate validate
e backup antes de implantação autorizada. Em rollback, desabilitar contato e
preservar tabela/consentimentos; não restaurar serialização privada como solução.
