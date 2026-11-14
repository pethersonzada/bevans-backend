package com.bevans.repository;
import com.bevans.model.ConviteExcursao;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ConviteExcursaoRepository extends JpaRepository<ConviteExcursao,Long> {
    Optional<ConviteExcursao> findByTokenHash(String hash);
    List<ConviteExcursao> findByExcursaoIdOrderByIdDesc(Long id);
}
