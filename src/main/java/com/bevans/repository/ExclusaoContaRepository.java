package com.bevans.repository;
import com.bevans.model.ExclusaoConta;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ExclusaoContaRepository extends JpaRepository<ExclusaoConta,Long>{
    boolean existsByFirebaseUid(String uid);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from ExclusaoConta e where e.estado in('PENDENTE','PROCESSANDO') and e.proximaTentativa<=:agora order by e.id")
    List<ExclusaoConta> pendentes(@Param("agora")Instant agora,Pageable pagina);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from ExclusaoConta e where e.id=:id")
    Optional<ExclusaoConta> lockById(@Param("id")Long id);
}
