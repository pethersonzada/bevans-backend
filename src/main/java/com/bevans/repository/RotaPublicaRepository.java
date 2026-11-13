package com.bevans.repository;
import com.bevans.model.RotaPublica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.*;
import java.util.List;
import java.util.Optional;
public interface RotaPublicaRepository extends JpaRepository<RotaPublica,Long>, JpaSpecificationExecutor<RotaPublica> {
    @Override
    @EntityGraph(attributePaths={"turma","turma.motorista"})
    Page<RotaPublica> findAll(Specification<RotaPublica> filtro,Pageable pagina);
    Optional<RotaPublica> findByTurmaId(Long turmaId);
    List<RotaPublica> findByTurmaMotoristaId(Long motoristaId);
}
