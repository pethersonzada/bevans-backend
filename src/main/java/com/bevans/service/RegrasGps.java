package com.bevans.service;
import com.bevans.dto.PosicaoRequest;
import com.bevans.model.AmostraGps;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** Mesma validação para viagem vinculada à turma e execução de excursão. */
public final class RegrasGps {
    private RegrasGps(){}
    public record Validada(PosicaoRequest dados,Instant recebida,boolean nova){}
    public static Validada validar(PosicaoRequest d,AmostraGps p,Instant inicio,Clock clock){
        if(d==null||d.amostraId()==null||!d.amostraId().matches("[A-Za-z0-9_-]{1,80}")||d.observadoEm()==null
                ||d.latitude()==null||d.longitude()==null||!Double.isFinite(d.latitude())||!Double.isFinite(d.longitude())
                ||Math.abs(d.latitude())>90||Math.abs(d.longitude())>180||(d.latitude()==0&&d.longitude()==0)
                ||d.precisao()==null||!Double.isFinite(d.precisao())||d.precisao()<0||d.precisao()>5000
                ||(d.velocidade()!=null&&(!Double.isFinite(d.velocidade())||d.velocidade()<0||d.velocidade()>100)))
            throw new IllegalArgumentException("Amostra GPS inválida; informe pin, instante e precisão em metros.");
        d=new PosicaoRequest(d.amostraId(),d.latitude(),d.longitude(),d.observadoEm().truncatedTo(ChronoUnit.MICROS),d.precisao(),d.velocidade());
        Instant agora=clock.instant().truncatedTo(ChronoUnit.MICROS);
        if(Objects.equals(p.getAmostraId(),d.amostraId())){
            if(!Objects.equals(p.getLatitude(),d.latitude())||!Objects.equals(p.getLongitude(),d.longitude())||!Objects.equals(p.getObservadoEm(),d.observadoEm())||!Objects.equals(p.getPrecisao(),d.precisao())||!Objects.equals(p.getVelocidade(),d.velocidade()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Identificador de amostra reutilizado com outros dados.");
            return new Validada(d,agora,false);
        }
        if(inicio!=null&&d.observadoEm().isBefore(inicio))throw new IllegalArgumentException("Amostra anterior ao início da viagem.");
        if(d.observadoEm().isBefore(agora.minusSeconds(30))||d.observadoEm().isAfter(agora.plusSeconds(5)))throw new IllegalArgumentException("Amostra antiga ou no futuro.");
        if(p.getObservadoEm()!=null&&!d.observadoEm().isAfter(p.getObservadoEm()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Amostra fora de ordem.");
        if(p.getRecebidoEm()!=null&&agora.isBefore(p.getRecebidoEm().plusSeconds(5)))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Envie no máximo uma amostra a cada 5 segundos.");
        return new Validada(d,agora,true);
    }
    public static String estado(boolean ativa,AmostraGps p,Clock clock){return !ativa?"FINALIZADA":p==null||p.getObservadoEm()==null?"SEM_LOCALIZACAO":!clock.instant().isBefore(p.getObservadoEm().plusSeconds(30))?"DESATUALIZADA":"ATUALIZADA";}
}
