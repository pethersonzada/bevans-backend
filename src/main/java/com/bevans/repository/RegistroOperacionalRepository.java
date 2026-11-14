package com.bevans.repository;
import com.bevans.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RegistroOperacionalRepository extends JpaRepository<RegistroOperacional,Long>{
    List<RegistroOperacional> findByOperacaoIdOrderByOrdemAscIdAsc(Long id);
    Optional<RegistroOperacional> findByOperacaoIdAndAlunoId(Long id,Long aluno);
    Optional<RegistroOperacional> findByOperacaoIdAndInscricaoId(Long id,Long inscricao);
    long countByOperacaoIdAndEstado(Long id,EstadoOperacional estado);
}
