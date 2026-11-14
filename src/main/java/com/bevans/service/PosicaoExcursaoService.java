package com.bevans.service;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.*;
import jakarta.persistence.*;
import java.time.*;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class PosicaoExcursaoService {
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ApplicationEventPublisher eventos;
    public record Acesso(Long credencialId,Instant expira){}
    private final ExecucaoExcursaoRepository execucoes;private final ExcursaoRepository excursoes;
    private final UsuarioRepository usuarios;private final CredencialInscricaoExcursaoRepository credenciais;
    private final InscricaoExcursaoRepository inscricoes;private final RegistroOperacionalRepository registros;
    private final OperacaoViagemRepository operacoes;private final PosicaoExecucaoExcursaoRepository posicoes;
    private final EntityManager em;private final Clock clock;
    public PosicaoExcursaoService(ExecucaoExcursaoRepository x,ExcursaoRepository e,UsuarioRepository u,CredencialInscricaoExcursaoRepository c,
            InscricaoExcursaoRepository i,RegistroOperacionalRepository r,OperacaoViagemRepository o,PosicaoExecucaoExcursaoRepository p,EntityManager em,Clock clock){execucoes=x;excursoes=e;usuarios=u;credenciais=c;inscricoes=i;registros=r;operacoes=o;posicoes=p;this.em=em;this.clock=clock;}
    private ResponseStatusException ausente(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Execução ou inscrição indisponível");}
    private ExecucaoExcursao execucao(Long id){return execucoes.findById(id).orElseThrow(this::ausente);}
    private void dono(ExecucaoExcursao x,String uid){var u=usuarios.findByFirebaseUid(uid).orElseThrow(()->new AccessDeniedException("Perfil indisponível"));if(!"MOTORISTA".equals(u.getTipo())||!x.getExcursao().getOrganizador().getId().equals(u.getId()))throw new AccessDeniedException("Execução de outro organizador");}
    @Transactional public PosicaoExcursaoDTO enviar(Long id,String uid,PosicaoRequest d){
        var x=execucao(id);dono(x,uid);var e=excursoes.lockById(x.getExcursao().getId()).orElseThrow(this::ausente);em.refresh(e,LockModeType.PESSIMISTIC_WRITE);em.refresh(x);dono(x,uid);
        if(x.getEstado()!=EstadoExecucaoExcursao.EM_ANDAMENTO||e.getEstado()==EstadoExcursao.CANCELADA)throw new ResponseStatusException(HttpStatus.CONFLICT,"Execução encerrada");
        var p=posicoes.findById(id).orElseGet(PosicaoExecucaoExcursao::new);var amostra=RegrasGps.validar(d,p,x.getIniciadaEm(),clock);
        if(amostra.nova()){p.atualizar(id,amostra.dados(),amostra.recebida());posicoes.saveAndFlush(p);eventos.publishEvent(new EventosDeViagem.Gps(TipoOperacao.EXCURSAO,id));}
        return dto(x,p);
    }
    @Transactional(readOnly=true) public PosicaoExcursaoDTO consultar(Long id,String uid){var x=execucao(id);dono(x,uid);return dto(x,posicoes.findById(id).orElse(null));}
    @Transactional(readOnly=true) public Acesso autorizar(Long id,String token){
        String hash;try{hash=TokensExcursao.hash(token);}catch(IllegalArgumentException e){throw ausente();}
        var c=credenciais.findByTokenHash(hash).orElseThrow(this::ausente);validar(execucao(id),c);return new Acesso(c.getId(),c.getExpiraEm());
    }
    private void validar(ExecucaoExcursao x,CredencialInscricaoExcursao c){
        if(!c.getExpiraEm().isAfter(clock.instant())||!x.getExcursao().getId().equals(c.getExcursao().getId()))throw ausente();
        var i=inscricoes.findByCredencialId(c.getId()).filter(v->v.getEstado()==StatusInscricaoExcursao.CONFIRMADA).orElseThrow(this::ausente);
        var o=operacoes.findByTipoAndReferenciaId(TipoOperacao.EXCURSAO,x.getId()).orElseThrow(this::ausente);
        if(registros.findByOperacaoIdAndInscricaoId(o.id,i.getId()).isEmpty())throw ausente();
    }
    @Transactional(readOnly=true) public PosicaoExcursaoDTO consultarParticipante(Long id,String token){var a=autorizar(id,token);return consultarCredencial(id,a.credencialId());}
    /** Somente adaptador SSE usa ID validado; cada emissão reconsulta validade e inscrição. */
    @Transactional(readOnly=true) public PosicaoExcursaoDTO consultarCredencial(Long id,Long credencialId){var x=execucao(id);validar(x,credenciais.findById(credencialId).orElseThrow(this::ausente));return dto(x,posicoes.findById(id).orElse(null));}
    private PosicaoExcursaoDTO dto(ExecucaoExcursao x,PosicaoExecucaoExcursao p){
        String estado=RegrasGps.estado(x.getEstado()==EstadoExecucaoExcursao.EM_ANDAMENTO&&x.getExcursao().getEstado()!=EstadoExcursao.CANCELADA,p,clock);boolean atual="ATUALIZADA".equals(estado);
        return new PosicaoExcursaoDTO(x.getId(),estado,atual?p.getLatitude():null,atual?p.getLongitude():null,p==null?null:p.getObservadoEm(),p==null?null:p.getRecebidoEm(),atual?p.getPrecisao():null,atual?p.getVelocidade():null,10);
    }
    @EventListener @Transactional public void encerramento(EventosDeViagem.Excursao evento){if("FINALIZADA".equals(evento.tipo())||"CANCELADA".equals(evento.tipo()))posicoes.findById(evento.id()).ifPresent(PosicaoExecucaoExcursao::limpar);}
}
