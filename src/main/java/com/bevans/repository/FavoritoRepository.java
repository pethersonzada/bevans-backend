package com.bevans.repository;
import com.bevans.model.FavoritoRota;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FavoritoRepository extends JpaRepository<FavoritoRota,Long> {
    Optional<FavoritoRota> findByUsuarioIdAndRotaId(Long usuarioId,Long rotaId);
    boolean existsByUsuarioIdAndRotaIdAndAtivoTrue(Long usuarioId,Long rotaId);
    List<FavoritoRota> findByUsuarioIdAndAtivoTrueAndRotaIdIn(Long usuarioId,List<Long> rotaIds);
    long countByUsuarioIdAndRotaId(Long usuarioId,Long rotaId);
}
