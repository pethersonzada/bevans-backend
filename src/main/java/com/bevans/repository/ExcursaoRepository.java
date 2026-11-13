package com.bevans.repository;
import com.bevans.model.Excursao;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ExcursaoRepository extends JpaRepository<Excursao,Long> {
    Page<Excursao> findByOrganizadorId(Long organizadorId,Pageable pagina);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from Excursao e where e.id=:id")
    Optional<Excursao> lockById(@Param("id")Long id);
}
