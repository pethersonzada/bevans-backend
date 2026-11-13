
package com.bevans.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bevans.model.AlunoTurma;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlunoTurmaRepository
        extends JpaRepository<AlunoTurma, Long> {

    long countByTurmaIdAndStatus(Long turmaId, String status);

    interface OcupacaoTurma { Long getTurmaId(); long getOcupados(); }
    @Query("select v.turma.id as turmaId, count(v.id) as ocupados from AlunoTurma v where v.turma.id in :ids and v.status='APROVADO' group by v.turma.id")
    List<OcupacaoTurma> ocupacaoDasTurmas(@Param("ids") List<Long> ids);

    @Query("select v.turma.id from AlunoTurma v where v.id = :id")
    Optional<Long> turmaIdDoVinculo(@Param("id") Long id);

    @Query("select v.status from AlunoTurma v where v.id = :id")
    String statusAtual(@Param("id") Long id);

    List<AlunoTurma> findByTurmaIdAndStatus(
            Long turmaId,
            String status
    );

    Optional<AlunoTurma> findByTurmaIdAndAlunoId(
            Long turmaId,
            Long alunoId
    );

    List<AlunoTurma> findByAlunoIdAndStatus(
            Long alunoId,
            String status
    );

    List<AlunoTurma> findByAlunoId(
            Long alunoId
    );

    boolean existsByTurmaIdAndAlunoIdAndStatus(
            Long turmaId,
            Long alunoId,
            String status
    );
}
