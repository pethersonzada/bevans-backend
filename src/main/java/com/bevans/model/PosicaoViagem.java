package com.bevans.model;

import com.bevans.dto.PosicaoRequest;
import jakarta.persistence.*;
import java.time.Instant;

/** Somente a última amostra; nunca um histórico de deslocamentos. */
@Entity @Table(name="posicao_viagem")
public class PosicaoViagem {
    @Id private Long viagemId;
    private String amostraId;
    private Double latitude;
    private Double longitude;
    private Double precisao;
    private Double velocidade;
    private Instant observadoEm;
    private Instant recebidoEm;
    public Long getViagemId(){return viagemId;}
    public String getAmostraId(){return amostraId;}
    public Double getLatitude(){return latitude;}
    public Double getLongitude(){return longitude;}
    public Double getPrecisao(){return precisao;}
    public Double getVelocidade(){return velocidade;}
    public Instant getObservadoEm(){return observadoEm;}
    public Instant getRecebidoEm(){return recebidoEm;}
    public void atualizar(Long id,PosicaoRequest d,Instant agora){
        viagemId=id;amostraId=d.amostraId();latitude=d.latitude();longitude=d.longitude();
        precisao=d.precisao();velocidade=d.velocidade();observadoEm=d.observadoEm();recebidoEm=agora;
    }
    public void limpar(){amostraId=null;latitude=null;longitude=null;precisao=null;velocidade=null;observadoEm=null;recebidoEm=null;}
}
