package com.bevans.repository;
import com.bevans.model.*;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OperacaoViagemRepository extends JpaRepository<OperacaoViagem,Long>{Optional<OperacaoViagem> findByTipoAndReferenciaId(TipoOperacao tipo,Long id);}
