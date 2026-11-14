package com.bevans.repository;
import com.bevans.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RegistroOperacionalRepository extends JpaRepository<RegistroOperacional,Long>{
    interface Resumo {
        Long getId();Long getAlunoId();Long getInscricaoId();EstadoOperacional getEstado();
        int getOrdem();double getLatitude();double getLongitude();StatusInscricaoExcursao getInscricaoEstado();
    }
    @org.springframework.data.jpa.repository.Query("select r.id as id,r.aluno.id as alunoId,i.id as inscricaoId,r.estado as estado,r.ordem as ordem,r.latitude as latitude,r.longitude as longitude,i.estado as inscricaoEstado from RegistroOperacional r left join r.inscricao i where r.operacao.id=:operacao order by r.ordem,r.id")
    List<Resumo> resumos(@org.springframework.data.repository.query.Param("operacao")Long operacao);
    long countByOperacaoId(Long operacao);
    long countByOperacaoIdAndEstadoIn(Long operacao,Collection<EstadoOperacional> estados);
    List<RegistroOperacional> findByOperacaoIdOrderByOrdemAscIdAsc(Long id);
    Optional<RegistroOperacional> findByOperacaoIdAndAlunoId(Long id,Long aluno);
    Optional<RegistroOperacional> findByOperacaoIdAndInscricaoId(Long id,Long inscricao);
    long countByOperacaoIdAndEstado(Long id,EstadoOperacional estado);
}
