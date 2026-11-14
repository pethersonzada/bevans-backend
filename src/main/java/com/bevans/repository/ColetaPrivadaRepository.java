package com.bevans.repository;
import com.bevans.model.ColetaPrivada;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ColetaPrivadaRepository extends JpaRepository<ColetaPrivada,Long>{Optional<ColetaPrivada> findByTurmaIdAndSentido(Long id,String sentido);}
