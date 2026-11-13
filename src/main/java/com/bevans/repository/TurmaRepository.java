package com.bevans.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.bevans.model.Turma;
import java.util.List;
import java.util.Optional;

public interface TurmaRepository extends JpaRepository<Turma, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Turma t WHERE t.id = :id")
    Optional<Turma> findByIdForUpdate(@Param("id") Long id);

    List<Turma> findByMotoristaId(Long motoristaId);
    Optional<Turma> findByCodigoConvite(String codigoConvite);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turma t where t.id = :id")
    Optional<Turma> lockById(@Param("id") Long id);

    @Query("select t.capacidade from Turma t where t.id = :id")
    Integer capacidadeAtual(@Param("id") Long id);
}
