package com.bevans.repository;
import com.bevans.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface EmbarqueRepository extends JpaRepository<SolicitacaoEmbarque,Long> {
    @Query("select e from SolicitacaoEmbarque e where e.rota.id=:rota and e.aluno.id=:aluno and e.dataOperacao=:data and e.sentido=:sentido and e.status in :status order by e.id")
    List<SolicitacaoEmbarque> abertos(@Param("rota") Long rota,@Param("aluno") Long aluno,@Param("data") LocalDate data,
            @Param("sentido") String sentido,@Param("status") Collection<StatusEmbarque> status);
    List<SolicitacaoEmbarque> findByAlunoIdOrderByIdDesc(Long alunoId);
    List<SolicitacaoEmbarque> findByRotaIdAndDataOperacaoAndSentidoOrderByIdAsc(Long rotaId,LocalDate data,String sentido);
    List<SolicitacaoEmbarque> findByRotaIdAndStatusAndDataOperacaoGreaterThanEqual(Long rotaId,StatusEmbarque status,LocalDate data);
}
