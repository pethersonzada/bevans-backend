package com.bevans.repository;
import com.bevans.model.ConviteResponsavel;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ConviteResponsavelRepository extends JpaRepository<ConviteResponsavel,Long>{
    Optional<ConviteResponsavel> findByTokenHash(String hash);
    @Modifying @Query("update ConviteResponsavel c set c.revogadoEm=:agora where c.passageiro.id=:id and c.consumidoEm is null and c.revogadoEm is null")
    int revogarPendentes(@Param("id")Long id,@Param("agora")Instant agora);
}
