package com.bevans.model;
import com.bevans.dto.PosicaoRequest;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
/** Campos comuns da última amostra, sem histórico de deslocamento. */
@MappedSuperclass
public abstract class AmostraGps {
    private String amostraId;
    private Double latitude,longitude,precisao,velocidade;
    private Instant observadoEm,recebidoEm;
    public String getAmostraId(){return amostraId;}public Double getLatitude(){return latitude;}public Double getLongitude(){return longitude;}
    public Double getPrecisao(){return precisao;}public Double getVelocidade(){return velocidade;}
    public Instant getObservadoEm(){return observadoEm;}public Instant getRecebidoEm(){return recebidoEm;}
    protected void preencher(PosicaoRequest d,Instant agora){amostraId=d.amostraId();latitude=d.latitude();longitude=d.longitude();precisao=d.precisao();velocidade=d.velocidade();observadoEm=d.observadoEm();recebidoEm=agora;}
    public void limpar(){amostraId=null;latitude=null;longitude=null;precisao=null;velocidade=null;observadoEm=null;recebidoEm=null;}
}
