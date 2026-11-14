package com.bevans.model;

import com.bevans.dto.PosicaoRequest;
import jakarta.persistence.*;
import java.time.Instant;

/** Somente a última amostra; nunca um histórico de deslocamentos. */
@Entity @Table(name="posicao_viagem")
public class PosicaoViagem extends AmostraGps {
    @Id private Long viagemId;
    public Long getViagemId(){return viagemId;}
    public void atualizar(Long id,PosicaoRequest d,Instant agora){
        viagemId=id;preencher(d,agora);
    }
}
