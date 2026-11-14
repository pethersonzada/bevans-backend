package com.bevans.repository;
import com.bevans.model.EventoResponsabilidade;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EventoResponsabilidadeRepository extends JpaRepository<EventoResponsabilidade,Long>{
    Page<EventoResponsabilidade> findByVinculoId(Long id,Pageable pagina);
}
