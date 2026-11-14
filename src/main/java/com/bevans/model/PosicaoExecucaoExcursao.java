package com.bevans.model;
import com.bevans.dto.PosicaoRequest;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="posicao_execucao_excursao")
public class PosicaoExecucaoExcursao extends AmostraGps {
    @Id private Long execucaoId;
    public void atualizar(Long id,PosicaoRequest d,Instant agora){execucaoId=id;preencher(d,agora);}
    public Long getExecucaoId(){return execucaoId;}
}
