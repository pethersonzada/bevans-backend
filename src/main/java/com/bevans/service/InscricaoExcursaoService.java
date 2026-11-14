package com.bevans.service;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InscricaoExcursaoService {
    private final ExcursaoRepository excursoes;private final InscricaoExcursaoRepository inscricoes;
    private final CredencialInscricaoExcursaoRepository credenciais;private final ParadaExcursaoRepository paradas;
    private final ConviteExcursaoService convites;private final ExcursaoService administracao;private final EntityManager em;private final Clock clock;
    public InscricaoExcursaoService(ExcursaoRepository e,InscricaoExcursaoRepository i,CredencialInscricaoExcursaoRepository c,
            ParadaExcursaoRepository p,ConviteExcursaoService v,ExcursaoService a,EntityManager em,Clock clock){
        excursoes=e;inscricoes=i;credenciais=c;paradas=p;convites=v;administracao=a;this.em=em;this.clock=clock;
    }
    private ResponseStatusException ausente(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Inscrição ou credencial indisponível.");}
    private ResponseStatusException conflito(String texto){return new ResponseStatusException(HttpStatus.CONFLICT,texto);}
    private Excursao lock(Long id){Excursao e=excursoes.lockById(id).orElseThrow(this::ausente);em.refresh(e,LockModeType.PESSIMISTIC_WRITE);return e;}
    private void aberta(Excursao e){if(e.getEstado()!=EstadoExcursao.PUBLICADA||!e.getPartidaEm().isAfter(clock.instant()))throw conflito("Inscrições encerradas nesta excursão.");}
    private CredencialInscricaoExcursao credencial(String token){
        String hash;try{hash=TokensExcursao.hash(token);}catch(IllegalArgumentException ex){throw ausente();}
        var c=credenciais.findByTokenHash(hash).orElseThrow(this::ausente);if(!c.getExpiraEm().isAfter(clock.instant()))throw ausente();return c;
    }
    @Transactional public CredencialInscricaoEmitidaDTO emitirCredencial(String convite){
        var v=convites.validar(convite);var e=lock(v.getExcursao().getId());em.refresh(v);convites.validar(convite);aberta(e);
        if(credenciais.countByExcursaoId(e.getId())>=2000)throw conflito("Limite de credenciais emitidas nesta excursão.");
        String token=TokensExcursao.gerar();Instant validade=clock.instant().plusSeconds(1800);
        if(validade.isAfter(e.getPartidaEm()))validade=e.getPartidaEm();
        var c=new CredencialInscricaoExcursao();c.criar(e,TokensExcursao.hash(token),validade);credenciais.saveAndFlush(c);
        return new CredencialInscricaoEmitidaDTO(token,validade);
    }
    private String texto(String valor,int limite){if(valor==null||valor.isBlank()||valor.trim().length()>limite)throw new IllegalArgumentException("Nome, contato ou ponto inválido.");return valor.trim();}
    private String contato(String valor){
        String s=texto(valor,150);if(s.contains("@")){if(!s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("Contato inválido.");return s.toLowerCase(Locale.ROOT);}
        if(!s.matches("[+0-9() .-]+"))throw new IllegalArgumentException("Informe telefone ou email de contato.");
        String n=s.replaceAll("[^0-9]","");if(n.length()<8||n.length()>15)throw new IllegalArgumentException("Telefone inválido.");return n;
    }
    private String identidade(String nome,String contato){
        String normal=java.text.Normalizer.normalize(nome,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((normal+"\u0000"+contato).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private record Ponto(ParadaExcursao parada,String nome,double lat,double lon){}
    private Ponto ponto(Excursao e,InscricaoExcursaoRequest d){
        if(e.getModoColeta()==ModoColetaExcursao.PORTA_A_PORTA){
            if(d.paradaId()!=null||!Boolean.TRUE.equals(d.pontoConfirmado()))throw new IllegalArgumentException("Confirme seu próprio pin de embarque.");
            String nome=texto(d.nomePonto(),120);Double lat=d.latitude(),lon=d.longitude();
            if(lat==null||lon==null||!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180||(lat==0&&lon==0))throw new IllegalArgumentException("Pin inválido.");
            return new Ponto(null,nome,lat,lon);
        }
        if(d.nomePonto()!=null||d.latitude()!=null||d.longitude()!=null||Boolean.TRUE.equals(d.pontoConfirmado()))throw new IllegalArgumentException("Neste modo selecione parada; não envie endereço residencial.");
        var ativas=paradas.findByExcursaoIdOrderByOrdemAscIdAsc(e.getId()).stream().filter(ParadaExcursao::isAtiva).toList();
        Long id=d.paradaId();
        if(e.getModoColeta()==ModoColetaExcursao.PARADA_UNICA&&id==null&&ativas.size()==1)id=ativas.get(0).getId();
        Long selecionada=id;var p=ativas.stream().filter(v->v.getId().equals(selecionada)).findFirst().orElseThrow(()->new IllegalArgumentException("Selecione parada ativa desta excursão."));
        return new Ponto(p,p.getNome(),p.getLatitude(),p.getLongitude());
    }
    @Transactional public InscricaoExcursaoDTO solicitar(String convite,String token,InscricaoExcursaoRequest d){
        var c=credencial(token);var e=lock(c.getExcursao().getId());em.refresh(c);credencial(token);aberta(e);
        if(d==null||!Boolean.TRUE.equals(d.aceitePrivacidade()))throw new IllegalArgumentException("Informe dados e aceite de privacidade.");
        String nome=texto(d.nome(),120),contato=contato(d.contato());Ponto p=ponto(e,d);
        var existente=inscricoes.findByCredencialId(c.getId()).orElse(null);
        if(existente!=null){
            if(!existente.getNome().equals(nome)||!existente.getContato().equals(contato)||!existente.getNomePonto().equals(p.nome())
                    ||existente.getLatitude()!=p.lat()||existente.getLongitude()!=p.lon()||!Objects.equals(existente.getParada()==null?null:existente.getParada().getId(),p.parada()==null?null:p.parada().getId()))
                throw conflito("Credencial já utilizada; consulte sua inscrição.");
            return dto(existente);
        }
        var v=convites.validar(convite);if(!v.getExcursao().getId().equals(e.getId()))throw ausente();
        inscricoes.expirar(e.getId(),clock.instant());String hash=identidade(nome,contato);
        if(!inscricoes.findByExcursaoIdAndParticipanteHashAndEstadoIn(e.getId(),hash,List.of(StatusInscricaoExcursao.SOLICITADA,StatusInscricaoExcursao.CONFIRMADA)).isEmpty())throw conflito("Inscrição já existente para este participante.");
        if(inscricoes.ocupacao(e.getId(),clock.instant())>=e.getCapacidade())throw conflito("Excursão sem vagas.");
        Instant ate=clock.instant().plusSeconds(900);if(ate.isAfter(e.getPartidaEm()))ate=e.getPartidaEm();
        var i=new InscricaoExcursao();i.criar(e,c,nome,contato,hash,p.parada(),p.nome(),p.lat(),p.lon(),clock.instant(),ate);
        c.prolongar((e.getRetornoEm()==null?e.getPartidaEm():e.getRetornoEm()).plusSeconds(7*86400));credenciais.save(c);
        return dto(inscricoes.saveAndFlush(i));
    }
    @Transactional(readOnly=true) public InscricaoExcursaoDTO consultar(String token){var c=credencial(token);return dto(inscricoes.findByCredencialId(c.getId()).orElseThrow(this::ausente));}
    @Transactional public InscricaoExcursaoDTO cancelar(String token){
        var c=credencial(token);var e=lock(c.getExcursao().getId());var i=inscricoes.findByCredencialId(c.getId()).orElseThrow(this::ausente);em.refresh(i);
        if(i.getEstado()==StatusInscricaoExcursao.CANCELADA||e.getEstado()==EstadoExcursao.CANCELADA)return dto(i);
        aberta(e);i.cancelar();return dto(inscricoes.saveAndFlush(i));
    }
    @Transactional public InscricaoExcursaoDTO confirmar(Long excursaoId,Long id,String uid){
        administracao.consultar(excursaoId,uid);var e=lock(excursaoId);aberta(e);
        var i=inscricoes.findById(id).filter(v->v.getExcursao().getId().equals(excursaoId)).orElseThrow(this::ausente);em.refresh(i);
        if(i.getEstado()==StatusInscricaoExcursao.CONFIRMADA)return dto(i);
        if(i.getEstado()!=StatusInscricaoExcursao.SOLICITADA||!i.getReservaValidaAte().isAfter(clock.instant()))throw conflito("Reserva expirada ou cancelada.");
        i.confirmar(clock.instant());return dto(inscricoes.saveAndFlush(i));
    }
    @Transactional(readOnly=true) public PaginaDTO<InscricaoExcursaoDTO> listar(Long id,String uid,int pagina,int tamanho){
        administracao.consultar(id,uid);if(pagina<0||pagina>10000||tamanho<1||tamanho>50)throw new IllegalArgumentException("Paginação inválida.");
        var p=inscricoes.findByExcursaoId(id,PageRequest.of(pagina,tamanho,Sort.by("id")));
        return new PaginaDTO<>(p.stream().map(this::dto).toList(),p.getTotalElements(),pagina,tamanho,p.getTotalPages());
    }
    @Transactional public InscricaoExcursaoDTO cancelarPeloDono(Long excursaoId,Long id,String uid){
        administracao.consultar(excursaoId,uid);var e=lock(excursaoId);
        var i=inscricoes.findById(id).filter(v->v.getExcursao().getId().equals(excursaoId)).orElseThrow(this::ausente);em.refresh(i);
        if(i.getEstado()==StatusInscricaoExcursao.CANCELADA)return dto(i);aberta(e);i.cancelar();return dto(inscricoes.saveAndFlush(i));
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public VagasExcursaoDTO vagas(Long id,String uid){
        var e=administracao.consultar(id,uid);long ocupadas=inscricoes.ocupacao(id,clock.instant());
        long confirmadas=inscricoes.countByExcursaoIdAndEstado(id,StatusInscricaoExcursao.CONFIRMADA);
        return new VagasExcursaoDTO(e.capacidade(),ocupadas-confirmadas,confirmadas,Math.max(0,e.capacidade()-ocupadas));
    }
    private InscricaoExcursaoDTO dto(InscricaoExcursao i){
        StatusInscricaoExcursao estado=i.getExcursao().getEstado()==EstadoExcursao.CANCELADA?StatusInscricaoExcursao.CANCELADA:i.getEstado();
        if(estado==StatusInscricaoExcursao.SOLICITADA&&!i.getReservaValidaAte().isAfter(clock.instant()))estado=StatusInscricaoExcursao.EXPIRADA;
        return new InscricaoExcursaoDTO(i.getId(),i.getNome(),estado,i.getParada()==null?null:i.getParada().getId(),i.getNomePonto(),i.getLatitude(),i.getLongitude(),i.getReservaValidaAte(),i.getConfirmadaEm());
    }
}
