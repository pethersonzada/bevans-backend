package com.bevans.service;
import com.bevans.repository.*;
import jakarta.persistence.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class CancelamentoViagemService {
    private final ViagemRepository viagens;private final TurmaRepository turmas;private final UsuarioRepository usuarios;
    private final PosicaoViagemRepository posicoes;private final ApplicationEventPublisher eventos;private final EntityManager em;
    public CancelamentoViagemService(ViagemRepository v,TurmaRepository t,UsuarioRepository u,PosicaoViagemRepository p,ApplicationEventPublisher e,EntityManager em){viagens=v;turmas=t;usuarios=u;posicoes=p;eventos=e;this.em=em;}
    @Transactional public void cancelar(Long id,String uid){
        var v=viagens.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Viagem não encontrada"));
        var t=turmas.lockById(v.getTurma().getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Turma não encontrada"));em.refresh(t,LockModeType.PESSIMISTIC_WRITE);em.refresh(v);
        var u=usuarios.findByFirebaseUid(uid).orElseThrow(()->new AccessDeniedException("Perfil indisponível"));
        if(!"MOTORISTA".equals(u.getTipo())||!t.getMotorista().getId().equals(u.getId()))throw new AccessDeniedException("Viagem de outro motorista");
        if("CANCELADA".equals(v.getStatus()))return;
        if(!"EM_ANDAMENTO".equals(v.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Viagem já finalizada");
        eventos.publishEvent(new EventosDeViagem.Viagem("VALIDAR_FIM",id));v.setStatus("CANCELADA");
        posicoes.findById(id).ifPresent(p->{p.limpar();posicoes.save(p);});viagens.saveAndFlush(v);
        eventos.publishEvent(new EventosDeViagem.Viagem("CANCELADA",id));
    }
}
