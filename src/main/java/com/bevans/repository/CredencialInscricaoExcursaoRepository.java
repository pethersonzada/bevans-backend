package com.bevans.repository;
import com.bevans.model.CredencialInscricaoExcursao;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CredencialInscricaoExcursaoRepository extends JpaRepository<CredencialInscricaoExcursao,Long>{
    Optional<CredencialInscricaoExcursao> findByTokenHash(String hash);
    long countByExcursaoId(Long id);
}
