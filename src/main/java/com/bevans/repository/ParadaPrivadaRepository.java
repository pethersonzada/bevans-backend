package com.bevans.repository;
import com.bevans.model.ParadaPrivada;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ParadaPrivadaRepository extends JpaRepository<ParadaPrivada,Long>{List<ParadaPrivada> findByColetaIdOrderByOrdemAscIdAsc(Long id);}
