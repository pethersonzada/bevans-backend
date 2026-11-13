package com.bevans.service;

import com.bevans.dto.PosicaoDTO;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE limitado, sem tokens armazenados e com autorização reavaliada a cada evento. */
@Service
public class PosicaoStreamService {
    private record Canal(Long viagem,String uid,Instant expira,SseEmitter emitter) {}
    private final Map<UUID,Canal> canais=new LinkedHashMap<>();
    private final PosicaoViagemService posicoes;
    private final Clock clock;
    public PosicaoStreamService(PosicaoViagemService posicoes,Clock clock){this.posicoes=posicoes;this.clock=clock;}
    public synchronized SseEmitter abrir(Long viagem,String uid,Instant tokenExpira){
        if(tokenExpira==null||!tokenExpira.isAfter(clock.instant()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Renove o token antes de conectar.");
        PosicaoDTO inicial=posicoes.consultar(viagem,uid);
        if(canais.size()>=200||canais.values().stream().filter(c->c.uid().equals(uid)).count()>=2)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Limite de conexões de acompanhamento.");
        Instant expira=tokenExpira.isBefore(clock.instant().plusSeconds(300))?tokenExpira:clock.instant().plusSeconds(300);
        SseEmitter emitter=new SseEmitter(Duration.between(clock.instant(),expira).toMillis());UUID id=UUID.randomUUID();
        canais.put(id,new Canal(viagem,uid,expira,emitter));
        emitter.onCompletion(()->remover(id));emitter.onTimeout(()->fechar(id));emitter.onError(e->remover(id));
        enviar(id,inicial);return emitter;
    }
    private synchronized void remover(UUID id){canais.remove(id);}
    private synchronized void fechar(UUID id){Canal c=canais.remove(id);if(c!=null)c.emitter().complete();}
    private void enviar(UUID id,PosicaoDTO d){
        Canal c=canais.get(id);if(c==null)return;
        try{
            c.emitter().send(SseEmitter.event().name("localizacao").reconnectTime(10000).data(d));
            if("FINALIZADA".equals(d.estado()))fechar(id);
        }catch(Exception e){fechar(id);}
    }
    @Scheduled(fixedDelay=10000)
    public synchronized void atualizar(){
        for(UUID id:new ArrayList<>(canais.keySet())){
            Canal c=canais.get(id);if(c==null)continue;
            if(!clock.instant().isBefore(c.expira())){fechar(id);continue;}
            try{enviar(id,posicoes.consultar(c.viagem(),c.uid()));}catch(Exception e){fechar(id);}
        }
    }
    int quantidade(){return canais.size();}
}
