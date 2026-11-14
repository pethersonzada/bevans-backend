package com.bevans.repository;
import com.bevans.model.ParadaExcursao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ParadaExcursaoRepository extends JpaRepository<ParadaExcursao,Long> {
    List<ParadaExcursao> findByExcursaoIdOrderByOrdemAscIdAsc(Long excursaoId);
}
