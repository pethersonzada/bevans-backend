package com.bevans.service;
import com.bevans.exception.*;
import com.bevans.model.ExclusaoConta;
import com.bevans.repository.*;
import jakarta.persistence.*;
import java.time.Clock;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ExclusaoContaService {
    public record Pedido(Long id,String uid,int tentativa){@Override public String toString(){return "ExclusaoPendente[identidade protegida]";}}
    private final UsuarioRepository usuarios;private final ExclusaoContaRepository exclusoes;private final EntityManager em;private final Clock clock;
    public ExclusaoContaService(UsuarioRepository u,ExclusaoContaRepository e,EntityManager em,Clock c){usuarios=u;exclusoes=e;this.em=em;clock=c;}
    @Transactional public void solicitar(Long id){
        var u=usuarios.lockById(id).orElseThrow(()->new RecursoNaoEncontradoException("Usuário não encontrado"));em.refresh(u,LockModeType.PESSIMISTIC_WRITE);
        if(u.getFirebaseUid()==null||u.getFirebaseUid().isBlank())throw new ConflitoException("Identidade não vinculada; exclusão exige tratamento específico");
        String uid=u.getFirebaseUid();
        // Sem cascata: FKs de histórico, menores, matrículas etc. impedem remoção sem política de retenção.
        try{usuarios.delete(u);usuarios.flush();}catch(org.springframework.dao.DataIntegrityViolationException ex){throw new ConflitoException("Conta possui dados vinculados; definir retenção/pseudonimização antes de excluir");}
        if(!exclusoes.existsByFirebaseUid(uid)){var e=new ExclusaoConta();e.firebaseUid=uid;e.solicitadaEm=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);e.proximaTentativa=e.solicitadaEm;exclusoes.saveAndFlush(e);}
    }
    @Transactional public Pedido reservar(){
        for(var e:exclusoes.pendentes(clock.instant(),PageRequest.of(0,10))){em.refresh(e);
            if(!java.util.Set.of("PENDENTE","PROCESSANDO").contains(e.estado)||e.proximaTentativa.isAfter(clock.instant()))continue;
            if(e.tentativas>=5){e.estado="FALHA";continue;}
            e.estado="PROCESSANDO";e.tentativas++;e.proximaTentativa=clock.instant().plusSeconds(120);return new Pedido(e.id,e.firebaseUid,e.tentativas);
        }return null;
    }
    @Transactional public void concluir(Pedido p,ExclusaoIdentidadeGateway.Resultado resultado){
        var e=exclusoes.lockById(p.id()).orElseThrow();em.refresh(e);if(!"PROCESSANDO".equals(e.estado)||e.tentativas!=p.tentativa())return;
        switch(resultado){
            case EXCLUIDA->{e.estado="CONCLUIDA";e.concluidaEm=clock.instant();}
            case PERMANENTE->e.estado="FALHA";
            case TRANSITORIO->{e.estado=e.tentativas>=5?"FALHA":"PENDENTE";e.proximaTentativa=clock.instant().plusSeconds(30L*(1L<<e.tentativas));}
            case DESATIVADO->{e.estado="PENDENTE";e.tentativas--;e.proximaTentativa=clock.instant().plusSeconds(3600);}
        }
    }
}
