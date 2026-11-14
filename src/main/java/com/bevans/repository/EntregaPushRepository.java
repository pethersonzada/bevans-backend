package com.bevans.repository;
import com.bevans.model.EntregaPush;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface EntregaPushRepository extends JpaRepository<EntregaPush,Long>{
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from EntregaPush e where e.estado in ('PENDENTE','ENVIANDO') and e.proximaTentativa<=:agora order by e.id")
    List<EntregaPush> pendentes(@Param("agora")Instant agora,Pageable pagina);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from EntregaPush e where e.id=:id")
    Optional<EntregaPush> lockById(@Param("id")Long id);
}
