package com.bevans.repository;
import com.bevans.model.ParadaPublica;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ParadaPublicaRepository extends JpaRepository<ParadaPublica,Long> {
    List<ParadaPublica> findByRotaIdOrderByOrdemAscIdAsc(Long rotaId);
}
