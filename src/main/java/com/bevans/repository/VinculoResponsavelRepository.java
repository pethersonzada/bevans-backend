package com.bevans.repository;
import com.bevans.model.VinculoResponsavel;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface VinculoResponsavelRepository extends JpaRepository<VinculoResponsavel,Long>{
    Optional<VinculoResponsavel> findByPassageiroIdAndResponsavelId(Long passageiro,Long responsavel);
    @Query("select v from VinculoResponsavel v where v.passageiro.id=:usuario or v.responsavel.id=:usuario")
    @EntityGraph(attributePaths={"passageiro","responsavel"})
    Page<VinculoResponsavel> proprios(@org.springframework.data.repository.query.Param("usuario")Long usuario,Pageable pagina);
}
