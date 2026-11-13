package com.bevans.repository;
import com.bevans.model.RotaPublica;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface RotaPublicaRepository extends JpaRepository<RotaPublica,Long> {
    Optional<RotaPublica> findByTurmaId(Long turmaId);
    List<RotaPublica> findByTurmaMotoristaId(Long motoristaId);
}
