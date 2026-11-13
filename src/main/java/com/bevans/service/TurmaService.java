
package com.bevans.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bevans.exception.TurmaComRotaAtivaException;
import com.bevans.exception.CapacidadeException;
import com.bevans.dto.VagasTurmaDTO;
import com.bevans.dto.SolicitacaoVagaDTO;
import com.bevans.exception.SolicitacaoConflictException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.bevans.model.AlunoTurma;
import com.bevans.model.Turma;
import com.bevans.model.Usuario;
import com.bevans.repository.AlunoTurmaRepository;
import com.bevans.repository.TurmaRepository;
import com.bevans.repository.UsuarioRepository;
import com.bevans.repository.ViagemRepository;

@Service
public class TurmaService {

    private final TurmaRepository turmaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final ViagemRepository viagemRepository;
    private final GeocodingService geocodingService;
    private final EntityManager entityManager;

    public TurmaService(
            TurmaRepository turmaRepository,
            UsuarioRepository usuarioRepository,
            AlunoTurmaRepository alunoTurmaRepository,
            ViagemRepository viagemRepository,
            GeocodingService geocodingService,
            EntityManager entityManager
    ) {
        this.turmaRepository = turmaRepository;
        this.usuarioRepository = usuarioRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.viagemRepository = viagemRepository;
        this.geocodingService = geocodingService;
        this.entityManager = entityManager;
    }

    private void validarCoordenadas(
            Double latitude,
            Double longitude,
            String campo
    ) {
        if (latitude == null
                || longitude == null
                || !Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || latitude < -90
                || latitude > 90
                || longitude < -180
                || longitude > 180
                || (latitude == 0.0 && longitude == 0.0)) {

            throw new IllegalArgumentException(
                    "Coordenadas de " + campo
                    + " inválidas ou incompletas."
            );
        }
    }

    private double[] resolverCoordenadas(
            Double latitude,
            Double longitude,
            String enderecoNome,
            String campo
    ) {
        // Coordenadas explícitas têm prioridade.
        // Um par incompleto é inválido.

        if (latitude != null || longitude != null) {
            validarCoordenadas(
                    latitude,
                    longitude,
                    campo
            );

            return new double[]{
                    latitude,
                    longitude
            };
        }

        // O rascunho pode não ter percurso. Nome isolado nunca representa
        // um ponto exato: a escolha deve ser confirmada no mapa.

        if (enderecoNome == null
                || enderecoNome.isBlank()) {

            return null;
        }

        throw new IllegalArgumentException(
                "Selecione e confirme o ponto de " + campo + " no mapa.");
    }

    public Turma criarTurma(Turma turma) {

        if (turma == null) {
            throw new IllegalArgumentException(
                    "Informe os dados da turma."
            );
        }

        if (turma.getMotorista() == null
                || turma.getMotorista().getId() == null) {

            throw new IllegalArgumentException(
                    "O ID do motorista é obrigatório "
                    + "para criar uma turma."
            );
        }

        Usuario motorista = usuarioRepository
                .findById(turma.getMotorista().getId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Motorista não encontrado."
                        )
                );

        if (!"MOTORISTA".equalsIgnoreCase(
                motorista.getTipo())) {

            throw new IllegalArgumentException(
                    "O usuário informado não é motorista."
            );
        }

        turma.setMotorista(motorista);
        turma.setId(null);
        turma.setExcluido(false);

        if (turma.getNome() == null
                || turma.getNome().isBlank()) {

            throw new IllegalArgumentException(
                    "Informe o nome da turma."
            );
        }

        turma.setNome(turma.getNome().trim());
        validarCapacidade(turma.getCapacidade());

        if (turma.getCodigoConvite() == null
                || turma.getCodigoConvite().isBlank()) {

            turma.setCodigoConvite(
                    UUID.randomUUID()
                            .toString()
                            .substring(0, 6)
                            .toUpperCase()
            );
        }

        // Validar destino antes de persistir.

        double[] destino = resolverCoordenadas(
                turma.getDestinoLatitude(),
                turma.getDestinoLongitude(),
                turma.getDestinoNome(),
                "destino"
        );

        // Validar origem antes de persistir.

        double[] origem = resolverCoordenadas(
                turma.getOrigemLatitude(),
                turma.getOrigemLongitude(),
                turma.getOrigemNome(),
                "origem"
        );

        if (destino != null) {
            turma.setDestinoLatitude(destino[0]);
            turma.setDestinoLongitude(destino[1]);
        }
        if (origem != null) {
            turma.setOrigemLatitude(origem[0]);
            turma.setOrigemLongitude(origem[1]);
        }

        // Nenhum endereço fictício é utilizado.
        return turmaRepository.save(turma);
    }

    public List<Turma> listarTurmasPorMotorista(
            Long motoristaId
    ) {
        return turmaRepository.findByMotoristaId(
                motoristaId
        );
    }

    public List<Usuario> listarPassageirosPorTurma(
            Long turmaId
    ) {
        List<AlunoTurma> vinculos =
                alunoTurmaRepository.findByTurmaIdAndStatus(
                        turmaId,
                        "APROVADO"
                );

        return vinculos.stream()
                .map(AlunoTurma::getAluno)
                .collect(Collectors.toList());
    }

    public void removerAluno(
            Long turmaId,
            Long alunoId
    ) {
        AlunoTurma vinculo = alunoTurmaRepository
                .findByTurmaIdAndAlunoId(
                        turmaId,
                        alunoId
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Vínculo não encontrado."
                        )
                );

        alunoTurmaRepository.delete(vinculo);
    }

    public List<Turma> listarTurmasDoAluno(
            Long alunoId
    ) {
        return alunoTurmaRepository
                .findByAlunoIdAndStatus(
                        alunoId,
                        "APROVADO"
                )
                .stream()
                .map(AlunoTurma::getTurma)
                .filter(turma -> turma != null)
                .filter(turma -> !turma.isExcluido())
                .distinct()
                .toList();
    }

    // Compatibilidade com o endpoint antigo.
    public Turma buscarTurmaPorAlunoId(
            Long alunoId
    ) {
        return listarTurmasDoAluno(alunoId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public Turma atualizarTurma(
            Long turmaId,
            Turma turmaAtualizada
    ) {
        if (turmaAtualizada == null) {
            throw new IllegalArgumentException(
                    "Informe os dados da atualização."
            );
        }

        Turma turma = turmaRepository
                .lockById(turmaId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Turma não encontrada."
                        )
                );
        entityManager.refresh(turma, LockModeType.PESSIMISTIC_WRITE);

        if (turmaAtualizada.getCapacidade() != null) {
            validarCapacidade(turmaAtualizada.getCapacidade());
            long ocupados = alunoTurmaRepository.countByTurmaIdAndStatus(turmaId, "APROVADO");
            if (turmaAtualizada.getCapacidade() < ocupados) {
                throw new CapacidadeException("A capacidade não pode ser menor que os passageiros aprovados (" + ocupados + ").");
            }
            turma.setCapacidade(turmaAtualizada.getCapacidade());
        }

        if (turmaAtualizada.getNome() != null
                && !turmaAtualizada.getNome().isBlank()) {

            turma.setNome(
                    turmaAtualizada.getNome().trim()
            );
        }

        if (turmaAtualizada.getTurno() != null
                && !turmaAtualizada.getTurno().isBlank()) {

            turma.setTurno(
                    turmaAtualizada.getTurno().trim()
            );
        }

        boolean destinoNomeMudou =
                turmaAtualizada.getDestinoNome() != null
                && !turmaAtualizada.getDestinoNome()
                        .equals(turma.getDestinoNome());

        boolean origemNomeMudou =
                turmaAtualizada.getOrigemNome() != null
                && !turmaAtualizada.getOrigemNome()
                        .equals(turma.getOrigemNome());

        // Atualizar destino somente se recebeu
        // coordenadas ou um nome diferente.

        if (turmaAtualizada.getDestinoLatitude() != null
                || turmaAtualizada.getDestinoLongitude() != null
                || destinoNomeMudou) {

            String nomeDestino =
                    turmaAtualizada.getDestinoNome() != null
                            ? turmaAtualizada.getDestinoNome()
                            : turma.getDestinoNome();

            double[] destino = resolverCoordenadas(
                    turmaAtualizada.getDestinoLatitude(),
                    turmaAtualizada.getDestinoLongitude(),
                    nomeDestino,
                    "destino"
            );

            if (destino == null) {
                throw new IllegalArgumentException("Confirme o destino no mapa.");
            }
            turma.setDestinoLatitude(destino[0]);
            turma.setDestinoLongitude(destino[1]);
        }

        // Atualizar origem somente se recebeu
        // coordenadas ou um nome diferente.

        if (turmaAtualizada.getOrigemLatitude() != null
                || turmaAtualizada.getOrigemLongitude() != null
                || origemNomeMudou) {

            String nomeOrigem =
                    turmaAtualizada.getOrigemNome() != null
                            ? turmaAtualizada.getOrigemNome()
                            : turma.getOrigemNome();

            double[] origem = resolverCoordenadas(
                    turmaAtualizada.getOrigemLatitude(),
                    turmaAtualizada.getOrigemLongitude(),
                    nomeOrigem,
                    "origem"
            );

            if (origem == null) {
                throw new IllegalArgumentException("Confirme a origem no mapa.");
            }
            turma.setOrigemLatitude(origem[0]);
            turma.setOrigemLongitude(origem[1]);
        }

        if (turmaAtualizada.getDestinoNome() != null) {
            turma.setDestinoNome(
                    turmaAtualizada.getDestinoNome()
            );
        }

        if (turmaAtualizada.getOrigemNome() != null) {
            turma.setOrigemNome(
                    turmaAtualizada.getOrigemNome()
            );
        }

        return turmaRepository.save(turma);
    }

    @Transactional
    public void solicitarEntradaPorCodigo(
            Long alunoId,
            String codigo
    ) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException(
                    "Informe o código de convite."
            );
        }

        Turma turma = turmaRepository
                .findByCodigoConvite(
                        codigo.trim().toUpperCase()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Código de convite inválido."
                        )
                );

        Usuario aluno = usuarioRepository
                .findById(alunoId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Aluno não encontrado."
                        )
                );

        turma = turmaRepository.lockById(turma.getId())
                .orElseThrow(() -> new IllegalArgumentException("Turma não encontrada."));

        if (!"PASSAGEIRO".equals(aluno.getTipo())) {
            throw new IllegalArgumentException(
                    "Somente passageiros podem solicitar entrada em turmas."
            );
        }

        AlunoTurma vinculo = alunoTurmaRepository
                .findByTurmaIdAndAlunoId(
                        turma.getId(),
                        alunoId
                )
                .orElse(null);

        if (vinculo != null) {

            if ("APROVADO".equals(vinculo.getStatus())) {
                throw new IllegalArgumentException(
                        "Você já participa desta turma."
                );
            }

            if ("PENDENTE".equals(vinculo.getStatus())) {
                throw new IllegalArgumentException(
                        "Sua solicitação já está pendente."
                );
            }

        } else {
            vinculo = new AlunoTurma();
            vinculo.setTurma(turma);
            vinculo.setAluno(aluno);
        }

        vinculo.setStatus("PENDENTE");

        alunoTurmaRepository.save(vinculo);
    }

    public List<AlunoTurma> listarAlunosPorStatus(
            Long turmaId,
            String status
    ) {
        return alunoTurmaRepository
                .findByTurmaIdAndStatus(
                        turmaId,
                        status
                );
    }

    @Transactional
    public void analisarSolicitacao(
            Long vinculoId,
            String status
    ) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "Informe APROVADO ou REJEITADO."
            );
        }

        String novoStatus = status.trim().toUpperCase(java.util.Locale.ROOT);

        if (!"APROVADO".equals(novoStatus)
                && !"REJEITADO".equals(novoStatus)) {
            throw new IllegalArgumentException(
                    "Status inválido. Use APROVADO ou REJEITADO."
            );
        }

        Long turmaId = alunoTurmaRepository.turmaIdDoVinculo(vinculoId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada."));
        turmaRepository.lockById(turmaId)
                .orElseThrow(() -> new IllegalArgumentException("Turma não encontrada."));

        // Consultas escalares depois do lock não reutilizam entidades antigas
        // do contexto da autorização/OpenEntityManagerInView.
        if (!"PENDENTE".equals(alunoTurmaRepository.statusAtual(vinculoId))) {
            throw new IllegalArgumentException("Somente solicitações pendentes podem ser analisadas.");
        }
        if ("APROVADO".equals(novoStatus)) {
            Integer capacidade = turmaRepository.capacidadeAtual(turmaId);
            if (capacidade == null) {
                throw new CapacidadeException("Configure a quantidade de assentos da turma antes de aprovar passageiros.");
            }
            if (alunoTurmaRepository.countByTurmaIdAndStatus(turmaId, "APROVADO") >= capacidade) {
                throw new CapacidadeException("Esta turma está sem vagas disponíveis.");
            }
        }

        AlunoTurma vinculo = alunoTurmaRepository
                .findById(vinculoId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Solicitação não encontrada."
                        )
                );

        if (!"PENDENTE".equals(vinculo.getStatus())) {
            throw new IllegalArgumentException(
                    "Somente solicitações pendentes podem ser analisadas."
            );
        }

        vinculo.setStatus(novoStatus);
        alunoTurmaRepository.save(vinculo);
    }

    private void validarCapacidade(Integer capacidade) {
        if (capacidade == null || capacidade <= 0) {
            throw new IllegalArgumentException("Informe uma quantidade inteira e positiva de assentos.");
        }
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoVagaDTO> minhasSolicitacoes(Long alunoId) {
        return alunoTurmaRepository.findByAlunoId(alunoId).stream()
                .filter(v -> v.getTurma() != null && !v.getTurma().isExcluido())
                .map(v -> new SolicitacaoVagaDTO(v.getId(), v.getTurma().getId(), v.getTurma().getNome(), v.getStatus()))
                .toList();
    }

    @Transactional
    public void cancelarSolicitacao(Long vinculoId, Long alunoId) {
        Long turmaId = alunoTurmaRepository.turmaIdDoVinculo(vinculoId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada."));
        turmaRepository.lockById(turmaId)
                .orElseThrow(() -> new IllegalArgumentException("Turma não encontrada."));
        AlunoTurma vinculo = alunoTurmaRepository.findById(vinculoId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada."));
        entityManager.refresh(vinculo);
        if (vinculo.getAluno() == null || !java.util.Objects.equals(alunoId, vinculo.getAluno().getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Solicitação de outro passageiro.");
        }
        if ("CANCELADO".equals(vinculo.getStatus())) return;
        if (!"PENDENTE".equals(vinculo.getStatus())) {
            throw new SolicitacaoConflictException("Somente solicitações pendentes podem ser canceladas.");
        }
        vinculo.setStatus("CANCELADO");
        alunoTurmaRepository.save(vinculo);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public VagasTurmaDTO consultarVagas(Long turmaId) {
        turmaRepository.findById(turmaId)
                .orElseThrow(() -> new IllegalArgumentException("Turma não encontrada."));
        Integer capacidade = turmaRepository.capacidadeAtual(turmaId);
        long ocupados = alunoTurmaRepository.countByTurmaIdAndStatus(turmaId, "APROVADO");
        return new VagasTurmaDTO(capacidade, ocupados,
                capacidade == null ? null : Math.max(0L, capacidade.longValue() - ocupados), capacidade != null);
    }

    @Transactional
    public Turma regenerarCodigoConvite(
            Long turmaId
    ) {
        Turma turma = turmaRepository
                .lockById(turmaId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Turma não encontrada."
                        )
                );

        entityManager.refresh(turma, LockModeType.PESSIMISTIC_WRITE);
        turma.setCodigoConvite(
                UUID.randomUUID()
                        .toString()
                        .substring(0, 6)
                        .toUpperCase()
        );

        return turmaRepository.save(turma);
    }

    @Transactional
    public void deletarTurma(Long turmaId) {
        Turma turma = turmaRepository
                .findById(turmaId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Turma não encontrada."
                        )
                );

        // Impede excluir uma turma com viagem ativa.
        viagemRepository.findFirstByTurmaIdAndStatus(
                turmaId,
                "EM_ANDAMENTO"
        ).ifPresent(viagem -> {
            throw new TurmaComRotaAtivaException(
                    "Não é possível excluir uma turma "
                    + "com rota em andamento."
            );
        });

        // Exclusão lógica, preservando histórico.
        turmaRepository.delete(turma);
    }
}
