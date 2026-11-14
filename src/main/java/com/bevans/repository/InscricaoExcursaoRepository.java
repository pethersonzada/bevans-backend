package com.bevans.repository;
import com.bevans.model.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface InscricaoExcursaoRepository extends JpaRepository<InscricaoExcursao,Long>{
    Optional<InscricaoExcursao> findByCredencialId(Long id);
    List<InscricaoExcursao> findByExcursaoIdAndParticipanteHashAndEstadoIn(Long id,String hash,List<StatusInscricaoExcursao> estados);
    Page<InscricaoExcursao> findByExcursaoId(Long id,Pageable pagina);
    List<InscricaoExcursao> findByExcursaoIdAndEstadoOrderByIdAsc(Long id,StatusInscricaoExcursao estado);
    long countByExcursaoIdAndEstado(Long id,StatusInscricaoExcursao estado);
    @Query("select count(i) from InscricaoExcursao i where i.excursao.id=:id and (i.estado='CONFIRMADA' or (i.estado='SOLICITADA' and i.reservaValidaAte>:agora))")
    long ocupacao(@Param("id")Long id,@Param("agora")Instant agora);
    @Modifying @Query("update InscricaoExcursao i set i.estado='EXPIRADA' where i.excursao.id=:id and i.estado='SOLICITADA' and i.reservaValidaAte<=:agora")
    int expirar(@Param("id")Long id,@Param("agora")Instant agora);
    @Modifying @Query("update InscricaoExcursao i set i.estado='CANCELADA' where i.excursao.id=:id and i.estado in ('SOLICITADA','CONFIRMADA')")
    int cancelarAtivas(@Param("id")Long id);
}
