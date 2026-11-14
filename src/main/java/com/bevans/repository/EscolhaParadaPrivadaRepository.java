package com.bevans.repository;
import com.bevans.model.EscolhaParadaPrivada;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EscolhaParadaPrivadaRepository extends JpaRepository<EscolhaParadaPrivada,Long>{
    Optional<EscolhaParadaPrivada> findByColetaIdAndAlunoId(Long coletaId,Long alunoId);
    List<EscolhaParadaPrivada> findByColetaIdAndAtivaTrue(Long id);
}
