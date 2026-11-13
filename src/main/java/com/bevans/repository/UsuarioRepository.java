package com.bevans.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.bevans.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where u.id=:id")
    Optional<Usuario> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByTelefone(String telefone);
    
    Optional<Usuario> findByFirebaseUid(String firebaseUid);

    List<Usuario> findByTipo(String tipo);
}
