package com.bevans.service;

import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PosicaoViagemService {
    private final ViagemRepository viagens;
    private final TurmaRepository turmas;
    private final UsuarioRepository usuarios;
    private final AlunoTurmaRepository vinculos;
    private final PosicaoViagemRepository posicoes;
    private final EntityManager em;
    private final Clock clock;
    public PosicaoViagemService(ViagemRepository viagens,TurmaRepository turmas,UsuarioRepository usuarios,
            AlunoTurmaRepository vinculos,PosicaoViagemRepository posicoes,EntityManager em,Clock clock){
        this.viagens=viagens;this.turmas=turmas;this.usuarios=usuarios;this.vinculos=vinculos;
        this.posicoes=posicoes;this.em=em;this.clock=clock;
    }
    private Viagem viagem(Long id){return viagens.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Viagem não encontrada."));}
    private Usuario usuario(String uid){return usuarios.findByFirebaseUid(uid).orElseThrow(()->new AccessDeniedException("Sessão sem perfil."));}
    private void dono(Viagem v,Usuario u){
        if(!"MOTORISTA".equals(u.getTipo())||v.getTurma()==null||v.getTurma().isExcluido()||!Objects.equals(v.getTurma().getMotorista().getId(),u.getId()))
            throw new AccessDeniedException("Viagem de outro motorista.");
    }
    private void leitor(Viagem v,Usuario u){
        if(v.getTurma()==null||v.getTurma().isExcluido())throw new AccessDeniedException("Viagem indisponível.");
        if("MOTORISTA".equals(u.getTipo())){dono(v,u);return;}
        if(!"PASSAGEIRO".equals(u.getTipo())||!vinculos.existsByTurmaIdAndAlunoIdAndStatus(v.getTurma().getId(),u.getId(),"APROVADO"))
            throw new AccessDeniedException("Passageiro sem autorização nesta viagem.");
    }
    private boolean ativo(Viagem v){return "EM_ANDAMENTO".equals(v.getStatus());}
    @Transactional
    public PosicaoDTO enviar(Long id,String uid,PosicaoRequest d){
        Viagem v=viagem(id);dono(v,usuario(uid));
        Turma t=turmas.lockById(v.getTurma().getId()).orElseThrow(()->new AccessDeniedException("Turma indisponível."));
        em.refresh(t,LockModeType.PESSIMISTIC_WRITE);em.refresh(v);dono(v,usuario(uid));
        if(!ativo(v))throw new ResponseStatusException(HttpStatus.CONFLICT,"Viagem encerrada.");
        if(d==null||d.amostraId()==null||!d.amostraId().matches("[A-Za-z0-9_-]{1,80}")||d.observadoEm()==null
                ||d.latitude()==null||d.longitude()==null||!Double.isFinite(d.latitude())||!Double.isFinite(d.longitude())
                ||Math.abs(d.latitude())>90||Math.abs(d.longitude())>180||(d.latitude()==0&&d.longitude()==0)
                ||d.precisao()==null||!Double.isFinite(d.precisao())||d.precisao()<0||d.precisao()>5000
                ||(d.velocidade()!=null&&(!Double.isFinite(d.velocidade())||d.velocidade()<0||d.velocidade()>100)))
            throw new IllegalArgumentException("Amostra GPS inválida; informe pin, instante e precisão em metros.");
        // PostgreSQL/H2 persistem microssegundos: normalização evita falso conflito no retry.
        d=new PosicaoRequest(d.amostraId(),d.latitude(),d.longitude(),d.observadoEm().truncatedTo(java.time.temporal.ChronoUnit.MICROS),d.precisao(),d.velocidade());
        PosicaoViagem p=posicoes.findById(id).orElseGet(PosicaoViagem::new);
        if(Objects.equals(p.getAmostraId(),d.amostraId())){
            if(!Objects.equals(p.getLatitude(),d.latitude())||!Objects.equals(p.getLongitude(),d.longitude())
                    ||!Objects.equals(p.getObservadoEm(),d.observadoEm())||!Objects.equals(p.getPrecisao(),d.precisao())||!Objects.equals(p.getVelocidade(),d.velocidade()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Identificador de amostra reutilizado com outros dados.");
            return dto(v,p); // Repetição não renova o TTL.
        }
        Instant agora=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if(v.getIniciadaEm()!=null&&d.observadoEm().isBefore(v.getIniciadaEm()))
            throw new IllegalArgumentException("Amostra anterior ao início da viagem.");
        if(d.observadoEm().isBefore(agora.minusSeconds(30))||d.observadoEm().isAfter(agora.plusSeconds(5)))
            throw new IllegalArgumentException("Amostra antiga ou no futuro.");
        if(p.getObservadoEm()!=null&&!d.observadoEm().isAfter(p.getObservadoEm()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Amostra fora de ordem.");
        if(p.getRecebidoEm()!=null&&agora.isBefore(p.getRecebidoEm().plusSeconds(5)))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Envie no máximo uma amostra a cada 5 segundos.");
        p.atualizar(id,d,agora);posicoes.saveAndFlush(p);return dto(v,p);
    }
    @Transactional(readOnly=true)
    public PosicaoDTO consultar(Long id,String uid){Viagem v=viagem(id);leitor(v,usuario(uid));return dto(v,posicoes.findById(id).orElse(null));}
    private PosicaoDTO dto(Viagem v,PosicaoViagem p){
        String estado=!ativo(v)?"FINALIZADA":p==null||p.getObservadoEm()==null?"SEM_LOCALIZACAO":
                !clock.instant().isBefore(p.getObservadoEm().plusSeconds(30))?"DESATUALIZADA":"ATUALIZADA";
        boolean atual="ATUALIZADA".equals(estado);
        return new PosicaoDTO(v.getId(),estado,atual?p.getLatitude():null,atual?p.getLongitude():null,
                p==null?null:p.getObservadoEm(),p==null?null:p.getRecebidoEm(),atual?p.getPrecisao():null,atual?p.getVelocidade():null,10);
    }
}
