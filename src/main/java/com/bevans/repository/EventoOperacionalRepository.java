package com.bevans.repository;
import com.bevans.model.EventoOperacional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EventoOperacionalRepository extends JpaRepository<EventoOperacional,Long>{List<EventoOperacional> findByRegistroIdOrderByIdAsc(Long id);}
