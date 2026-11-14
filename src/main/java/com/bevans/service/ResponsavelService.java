package com.bevans.service;

import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResponsavelService {
    private final UsuarioRepository usuarios;
    private final VinculoResponsavelRepository vinculos;
    private final ConviteResponsavelRepository convites;
    private final EventoResponsabilidadeRepository eventos;
    private final OperacaoViagemRepository operacoes;
    private final RegistroOperacionalRepository registros;
    private final AlunoTurmaRepository matriculas;
    private final PosicaoViagemService gps;
    private final VerificacaoResponsabilidade verificacao;
    private final EntityManager em;
    private final Clock clock;
    private final String termo;

    public ResponsavelService(UsuarioRepository u,VinculoResponsavelRepository v,ConviteResponsavelRepository c,
            EventoResponsabilidadeRepository e,OperacaoViagemRepository o,RegistroOperacionalRepository r,
            AlunoTurmaRepository a,PosicaoViagemService g,VerificacaoResponsabilidade verificacao,
            EntityManager em,Clock clock,@Value("${app.responsaveis.termo-versao:1}")String termo){
        usuarios=u;vinculos=v;convites=c;eventos=e;operacoes=o;registros=r;matriculas=a;gps=g;
        this.verificacao=verificacao;this.em=em;this.clock=clock;this.termo=termo;
        if(termo.isBlank()||termo.length()>40)throw new IllegalArgumentException("Versão de termo inválida");
    }
    private ResponseStatusException ausente(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Vínculo ou convite indisponível");}
    private ResponseStatusException conflito(){return new ResponseStatusException(HttpStatus.CONFLICT,"Vínculo mudou ou foi revogado; atualize os dados");}
    private Usuario usuario(String uid){if(uid==null||uid.isBlank())throw new AccessDeniedException("Sessão obrigatória");return usuarios.findByFirebaseUid(uid).orElseThrow(()->new AccessDeniedException("Perfil indisponível"));}
    private void papel(Usuario u,String tipo){if(!tipo.equals(u.getTipo()))throw new AccessDeniedException("Papel não autorizado");}
    private void consentimento(Boolean aceite,String versao){if(!Boolean.TRUE.equals(aceite)||!termo.equals(versao))throw new IllegalArgumentException("Informe aceite explícito e versão vigente do termo");}
    private Usuario lockPassageiro(Long id){var u=usuarios.lockById(id).orElseThrow(this::ausente);em.refresh(u,LockModeType.PESSIMISTIC_WRITE);papel(u,"PASSAGEIRO");return u;}
    private VinculoResponsavel proprio(Long id,Usuario u){return vinculos.findById(id).filter(v->v.passageiro.getId().equals(u.getId())||v.responsavel.getId().equals(u.getId())).orElseThrow(this::ausente);}
    private void versao(VinculoResponsavel v,Long versao){if(versao==null||versao!=v.versao)throw conflito();}

    @Transactional public ResponsavelDTO.Convite emitirConvite(String uid){
        var p=usuario(uid);papel(p,"PASSAGEIRO");lockPassageiro(p.getId());
        convites.revogarPendentes(p.getId(),clock.instant());String token=TokensExcursao.gerar();
        var c=new ConviteResponsavel();c.passageiro=p;c.tokenHash=TokensExcursao.hash(token);c.expiraEm=clock.instant().plusSeconds(900);convites.save(c);
        return new ResponsavelDTO.Convite(token,c.expiraEm);
    }
    @Transactional public void revogarConvites(String uid){var p=usuario(uid);papel(p,"PASSAGEIRO");lockPassageiro(p.getId());convites.revogarPendentes(p.getId(),clock.instant());}
    @Transactional public ResponsavelDTO solicitar(String uid,ResponsavelDTO.Solicitar d){
        var r=usuario(uid);papel(r,"RESPONSAVEL");if(d==null)throw new IllegalArgumentException("Informe convite e consentimento");consentimento(d.aceite(),d.termoVersao());
        String hash;try{hash=TokensExcursao.hash(d.token());}catch(IllegalArgumentException ex){throw ausente();}
        var c=convites.findByTokenHash(hash).orElseThrow(this::ausente);lockPassageiro(c.passageiro.getId());em.refresh(c);
        if(c.revogadoEm!=null||c.consumidoEm!=null||!c.expiraEm.isAfter(clock.instant()))throw ausente();
        var v=vinculos.findByPassageiroIdAndResponsavelId(c.passageiro.getId(),r.getId()).orElseGet(VinculoResponsavel::new);
        if(v.id!=null&&v.estado!=EstadoResponsabilidade.REVOGADO)throw conflito();
        v.passageiro=c.passageiro;v.responsavel=r;v.estado=EstadoResponsabilidade.SOLICITADO;v.criadoEm=clock.instant();
        v.consentimentoResponsavelEm=clock.instant();v.termoResponsavel=termo;v.consentimentoPassageiroEm=null;v.termoPassageiro=null;
        v.verificadoEm=null;v.referenciaVerificacao=null;v.revogadoEm=null;c.consumidoEm=clock.instant();
        salvarEvento(v,r,"SOLICITADO");return dto(v);
    }
    @Transactional public ResponsavelDTO aprovar(Long id,String uid,ResponsavelDTO.Aprovar d){
        var p=usuario(uid);papel(p,"PASSAGEIRO");var v=proprio(id,p);lockPassageiro(v.passageiro.getId());em.refresh(v);
        if(!v.passageiro.getId().equals(p.getId()))throw ausente();if(d==null)throw new IllegalArgumentException("Informe aprovação");
        versao(v,d.versao());consentimento(d.aceite(),d.termoVersao());if(v.estado==EstadoResponsabilidade.REVOGADO||!termo.equals(v.termoResponsavel))throw conflito();papel(v.responsavel,"RESPONSAVEL");
        var prova=verificacao.verificar(v.responsavel.getFirebaseUid(),p.getFirebaseUid());boolean comprovada=prova!=null&&prova.comprovada()&&prova.referenciaServidor()!=null&&!prova.referenciaServidor().isBlank()&&prova.referenciaServidor().length()<=200;
        var novo=comprovada?EstadoResponsabilidade.ATIVO:EstadoResponsabilidade.AGUARDANDO_VERIFICACAO;
        if(v.estado==novo&&termo.equals(v.termoPassageiro)&&(!comprovada||Objects.equals(v.referenciaVerificacao,prova.referenciaServidor())))return dto(v);
        v.consentimentoPassageiroEm=clock.instant();v.termoPassageiro=termo;v.estado=novo;
        v.verificadoEm=comprovada?clock.instant():null;v.referenciaVerificacao=comprovada?prova.referenciaServidor():null;
        salvarEvento(v,p,comprovada?"APROVADO":"CONSENTIMENTO_REGISTRADO");return dto(v);
    }
    @Transactional public ResponsavelDTO revogar(Long id,String uid,ResponsavelDTO.Revogar d){
        var u=usuario(uid);var v=proprio(id,u);lockPassageiro(v.passageiro.getId());em.refresh(v);
        if(v.estado==EstadoResponsabilidade.REVOGADO)return dto(v);versao(v,d==null?null:d.versao());
        v.estado=EstadoResponsabilidade.REVOGADO;v.revogadoEm=clock.instant();salvarEvento(v,u,"CONSENTIMENTO_REVOGADO");return dto(v);
    }
    private void salvarEvento(VinculoResponsavel v,Usuario autor,String acao){
        vinculos.saveAndFlush(v);var e=new EventoResponsabilidade();e.vinculo=v;e.autor=autor;e.acao=acao;e.estado=v.estado;e.criadoEm=clock.instant();e.versaoVinculo=v.versao;e.termoVersao=termo;e.referenciaVerificacao=v.referenciaVerificacao;eventos.saveAndFlush(e);
    }
    private Pageable pagina(int pagina,int tamanho){if(pagina<0||pagina>10000||tamanho<1||tamanho>50)throw new IllegalArgumentException("Paginação inválida");return PageRequest.of(pagina,tamanho,Sort.by(Sort.Direction.DESC,"id"));}
    @Transactional(readOnly=true) public Page<ResponsavelDTO> listar(String uid,int pagina,int tamanho){var u=usuario(uid);return vinculos.proprios(u.getId(),pagina(pagina,tamanho)).map(this::dto);}
    @Transactional(readOnly=true) public Page<ResponsavelDTO.Evento> historico(Long id,String uid,int pagina,int tamanho){proprio(id,usuario(uid));return eventos.findByVinculoId(id,pagina(pagina,tamanho)).map(e->new ResponsavelDTO.Evento(e.acao,e.estado,e.autor.getId(),e.criadoEm,e.versaoVinculo,e.termoVersao));}
    private RegistroOperacional autorizado(Long id,Long viagem,String uid){
        var r=usuario(uid);papel(r,"RESPONSAVEL");var v=proprio(id,r);lockPassageiro(v.passageiro.getId());em.refresh(v);
        if(!v.responsavel.getId().equals(r.getId())||v.estado!=EstadoResponsabilidade.ATIVO||!termo.equals(v.termoPassageiro)||!termo.equals(v.termoResponsavel)||v.verificadoEm==null)throw new AccessDeniedException("Vínculo sem autorização comprovada vigente");
        var prova=verificacao.verificar(r.getFirebaseUid(),v.passageiro.getFirebaseUid());
        if(prova==null||!prova.comprovada()||!Objects.equals(prova.referenciaServidor(),v.referenciaVerificacao))throw new AccessDeniedException("Comprovação indisponível ou revogada");
        var o=operacoes.findByTipoAndReferenciaId(TipoOperacao.VIAGEM,viagem).orElseThrow(this::ausente);
        if(!"EM_ANDAMENTO".equals(o.viagem.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Viagem encerrada");
        if(!matriculas.existsByTurmaIdAndAlunoIdAndStatus(o.viagem.getTurma().getId(),v.passageiro.getId(),"APROVADO"))throw new AccessDeniedException("Passageiro sem matrícula vigente");
        return registros.findByOperacaoIdAndAlunoId(o.id,v.passageiro.getId()).orElseThrow(this::ausente);
    }
    @Transactional public OperacaoDTO.Registro operacao(Long id,Long viagem,String uid){var r=autorizado(id,viagem,uid);return new OperacaoDTO.Registro(r.id,r.aluno.getId(),null,r.nome,r.estado,r.versao,r.autor,r.origem,r.atualizadoEm,r.paradaId,r.ordem);}
    @Transactional public PosicaoDTO localizacao(Long id,Long viagem,String uid){var r=autorizado(id,viagem,uid);return gps.consultar(viagem,r.aluno.getFirebaseUid());}
    private ResponsavelDTO dto(VinculoResponsavel v){return new ResponsavelDTO(v.id,v.passageiro.getId(),v.responsavel.getId(),v.estado,v.versao,v.consentimentoPassageiroEm,v.consentimentoResponsavelEm,v.termoPassageiro,v.termoResponsavel,v.verificadoEm,v.revogadoEm);}
}
