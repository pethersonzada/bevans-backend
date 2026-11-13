# Catálogo urbano — backend #34

GET /rotas-publicas/catalogo exige sessão Firebase com perfil cadastrado. Não
exige pertencer à rota. Retorna somente publicações ativas com turma não excluída
e capacidade configurada. Turmas privadas não são convertidas nem listadas.

Filtros opcionais combináveis: q (nome/destino/campus/região), cidade (cidade de
operação urbana, igualdade), destino/campus/regiao (trecho), turno (igualdade),
categoria ESCOLAR/UNIVERSITARIA/OUTROS, dia MONDAY..SUNDAY e somenteComVagas.
Cidade não é interpretada como viagem intermunicipal. Busca usa a mesma função
lower/translate em campo e parâmetro, ignorando maiúsculas e acentos latinos
incluídos no alfabeto documentado no código; não exige extensão PostgreSQL.
% e _ digitados são literais, não curingas arbitrários.

pagina começa em 0, tamanho padrão 20 e máximo 50; página máxima 10000. Ordem
estável por ID ascendente. Resposta: itens, total, pagina, tamanho, totalPaginas;
sem correspondência retorna itens vazio. Filtros textuais limitados a 160 chars.

Cada resumo expõe ID, nome, nome público do motorista, cidade de operação,
categoria/modalidade, destino, campus/região, turno, dias/horários e vagas.
Não há perfil de usuário completo, telefone, convite privado, coordenadas de
passageiro, pontos de embarque residenciais, listas nominais ou GPS.

Ocupação conta vínculos APROVADO não excluídos; pendentes não consomem vaga.
Consulta agregada por lote evita uma contagem por item; limite de página e
batch de dias limitam carregamentos. REPEATABLE_READ mantém filtro de vagas e
resumo no mesmo snapshot. Índices incrementais: 004-catalogo-indices.sql.

Validação: cinco cenários de filtros/acentos/página/capacidade/privacidade em H2
e os mesmos em PostgreSQL real isolado, além de verificações no fluxo HTTP real.
Migrações anteriores continuam sendo executadas no perfil PostgreSQL.
Nenhum arquivo ou fluxo de frontend é implementado por este contrato.
