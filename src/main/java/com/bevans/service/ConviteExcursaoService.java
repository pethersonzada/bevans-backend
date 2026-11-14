package com.bevans.service;
import com.bevans.model.*;
import com.bevans.repository.*;
import com.bevans.dto.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConviteExcursaoService {
    private final ConviteExcursaoRepository convites;private final ExcursaoRepository excursoes;
    private final UsuarioRepository usuarios;private final ParadaExcursaoRepository paradas;private final EntityManager em;private final Clock clock;
    private final InscricaoExcursaoRepository inscricoes;
    private final ExecucaoExcursaoRepository execucoes;
    public ConviteExcursaoService(ConviteExcursaoRepository c,ExcursaoRepository e,UsuarioRepository u,ParadaExcursaoRepository p,EntityManager em,Clock clock,InscricaoExcursaoRepository inscricoes,ExecucaoExcursaoRepository execucoes){convites=c;excursoes=e;usuarios=u;paradas=p;this.em=em;this.clock=clock;this.inscricoes=inscricoes;this.execucoes=execucoes;}
    private ResponseStatusException indisponivel(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Convite indisponível.");}
    private Excursao dono(Long id,String uid,boolean lock){
        Usuario u=usuarios.findByFirebaseUid(uid).filter(v->"MOTORISTA".equals(v.getTipo())).orElseThrow(()->new AccessDeniedException("Organizador inválido."));
        Excursao e=(lock?excursoes.lockById(id):excursoes.findById(id)).orElseThrow(this::indisponivel);
        if(lock)em.refresh(e,LockModeType.PESSIMISTIC_WRITE);
        if(!Objects.equals(e.getOrganizador().getId(),u.getId()))throw new AccessDeniedException("Excursão de outro organizador.");return e;
    }
    private void disponivel(Excursao e){if(e.getEstado()!=EstadoExcursao.PUBLICADA||!e.getPartidaEm().isAfter(clock.instant())||execucoes.existsByExcursaoId(e.getId()))throw indisponivel();}
    @Transactional public ConviteEmitidoDTO emitir(Long id,String uid,Instant expira){
        Excursao e=dono(id,uid,true);disponivel(e);
        if(expira==null||!expira.isAfter(clock.instant())||expira.isAfter(e.getPartidaEm())||expira.isAfter(clock.instant().plus(Duration.ofDays(30))))throw new IllegalArgumentException("Validade deve terminar antes da partida e em até 30 dias.");
        if(convites.findByExcursaoIdOrderByIdDesc(id).size()>=100)throw new ResponseStatusException(HttpStatus.CONFLICT,"Limite de 100 convites por excursão.");
        String token=TokensExcursao.gerar();ConviteExcursao c=new ConviteExcursao();c.criar(e,TokensExcursao.hash(token),expira,clock.instant());convites.saveAndFlush(c);return new ConviteEmitidoDTO(dto(c),token);
    }
    @Transactional public void revogar(Long id,Long conviteId,String uid){
        dono(id,uid,true);ConviteExcursao c=convites.findById(conviteId).filter(v->v.getExcursao().getId().equals(id)).orElseThrow(this::indisponivel);
        em.refresh(c);c.revogar(clock.instant());convites.save(c);
    }
    @Transactional(readOnly=true) public List<ConviteExcursaoDTO> listar(Long id,String uid){dono(id,uid,false);return convites.findByExcursaoIdOrderByIdDesc(id).stream().map(this::dto).toList();}
    @Transactional(readOnly=true) public ExcursaoConvitePublicoDTO consultar(String token){
        ConviteExcursao c=validar(token);Excursao e=c.getExcursao();
        var pontos=paradas.findByExcursaoIdOrderByOrdemAscIdAsc(e.getId()).stream().filter(ParadaExcursao::isAtiva)
                .map(p->new ParadaExcursaoDTO(p.getId(),p.getOrdem(),p.getNome(),p.getLatitude(),p.getLongitude(),p.getHorario(),p.getInstrucoes())).toList();
        return new ExcursaoConvitePublicoDTO(e.getTitulo(),e.getDestinoNome(),e.getDestinoLatitude(),e.getDestinoLongitude(),e.getPartidaEm(),e.getRetornoEm(),e.getModoColeta(),pontos,Math.max(0,e.getCapacidade()-inscricoes.ocupacao(e.getId(),clock.instant())));
    }
    @Transactional(readOnly=true) public ConviteExcursao validar(String token){
        String hash;try{hash=TokensExcursao.hash(token);}catch(IllegalArgumentException ex){throw indisponivel();}
        ConviteExcursao c=convites.findByTokenHash(hash).orElseThrow(this::indisponivel);
        if(c.getRevogadoEm()!=null||!c.getExpiraEm().isAfter(clock.instant()))throw indisponivel();disponivel(c.getExcursao());return c;
    }
    private ConviteExcursaoDTO dto(ConviteExcursao c){return new ConviteExcursaoDTO(c.getId(),c.getExpiraEm(),c.getCriadoEm(),c.getRevogadoEm());}
}
