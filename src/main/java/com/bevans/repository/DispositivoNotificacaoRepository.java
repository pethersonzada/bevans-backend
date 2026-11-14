package com.bevans.repository;
import com.bevans.model.DispositivoNotificacao;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DispositivoNotificacaoRepository extends JpaRepository<DispositivoNotificacao,Long>{
    Optional<DispositivoNotificacao> findByInstalacaoId(String id);
    Optional<DispositivoNotificacao> findByTokenHash(String hash);
    List<DispositivoNotificacao> findByUsuarioIdAndAtivoTrue(Long id);
}
