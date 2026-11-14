package com.bevans.repository;
import com.bevans.model.ExecucaoExcursao;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ExecucaoExcursaoRepository extends JpaRepository<ExecucaoExcursao,Long>{
    boolean existsByExcursaoId(Long id);
    Optional<ExecucaoExcursao> findByExcursaoIdAndSentido(Long id,String sentido);
    List<ExecucaoExcursao> findByExcursaoIdOrderByIdAsc(Long id);
    @Modifying @Query("update ExecucaoExcursao e set e.estado='CANCELADA',e.finalizadaEm=:agora,e.versao=e.versao+1 where e.excursao.id=:id and e.estado='EM_ANDAMENTO'")
    int cancelarAtivas(@Param("id")Long id,@Param("agora")Instant agora);
}
