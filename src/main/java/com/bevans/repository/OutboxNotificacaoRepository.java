package com.bevans.repository;
import com.bevans.model.OutboxNotificacao;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface OutboxNotificacaoRepository extends JpaRepository<OutboxNotificacao,Long>{
    boolean existsByChave(String chave);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from OutboxNotificacao o where o.estado in ('PENDENTE','PROCESSANDO') and o.proximaTentativa<=:agora order by o.id")
    List<OutboxNotificacao> pendentes(@Param("agora")Instant agora,Pageable pagina);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from OutboxNotificacao o where o.id=:id")
    Optional<OutboxNotificacao> lockById(@Param("id")Long id);
}
