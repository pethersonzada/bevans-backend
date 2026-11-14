package com.bevans.repository;
import com.bevans.model.Notificacao;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface NotificacaoRepository extends JpaRepository<Notificacao,Long>{
    boolean existsByChave(String chave);
    Page<Notificacao> findByUsuarioId(Long id,Pageable pagina);
    Page<Notificacao> findByInscricaoId(Long id,Pageable pagina);
    long countByUsuarioIdAndLidaEmIsNull(Long id);
    Optional<Notificacao> findByIdAndUsuarioId(Long id,Long usuario);
    Optional<Notificacao> findByIdAndInscricaoId(Long id,Long inscricao);
    @Modifying @Query("update Notificacao n set n.lidaEm=:agora where n.usuarioId=:id and n.lidaEm is null")
    int lerTodas(@Param("id")Long id,@Param("agora")Instant agora);
}
