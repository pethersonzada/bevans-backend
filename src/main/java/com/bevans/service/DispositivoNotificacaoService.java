package com.bevans.service;
import com.bevans.dto.NotificacaoDTO.*;
import com.bevans.model.*;
import com.bevans.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class DispositivoNotificacaoService {
    private final UsuarioRepository usuarios;private final DispositivoNotificacaoRepository dispositivos;
    private final PreferenciaNotificacaoRepository preferencias;private final Clock clock;
    public DispositivoNotificacaoService(UsuarioRepository u,DispositivoNotificacaoRepository d,PreferenciaNotificacaoRepository p,Clock c){usuarios=u;dispositivos=d;preferencias=p;clock=c;}
    private Long usuario(String uid){return usuarios.findByFirebaseUid(uid).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN)).getId();}
    @Transactional(readOnly=true) public Preferencias preferencias(String uid){var p=preferencias.findById(usuario(uid)).orElse(null);return p==null?new Preferencias(true,true):new Preferencias(p.pushOperacional,p.pushInformativo);}
    @Transactional public Preferencias preferencias(String uid,Preferencias pedido){
        if(pedido==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Long id=usuario(uid);usuarios.lockById(id).orElseThrow();var p=preferencias.findById(id).orElseGet(PreferenciaNotificacao::new);
        p.usuarioId=id;p.pushOperacional=pedido.pushOperacional();p.pushInformativo=pedido.pushInformativo();preferencias.save(p);return pedido;
    }
    @Transactional public DispositivoInfo registrar(String uid,Dispositivo pedido){
        if(pedido==null||!Boolean.TRUE.equals(pedido.consentePush())||pedido.instalacaoId()==null||!pedido.instalacaoId().matches("[A-Za-z0-9_-]{16,128}")||pedido.token()==null||pedido.token().length()<16||pedido.token().length()>4096||pedido.token().chars().anyMatch(c->c<=32||c>126))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dispositivo ou consentimento inválido");
        Long id=usuario(uid);usuarios.lockById(id).orElseThrow();
        var d=dispositivos.findByInstalacaoId(pedido.instalacaoId()).orElseGet(DispositivoNotificacao::new);
        if(d.usuarioId!=null&&!d.usuarioId.equals(id))throw new ResponseStatusException(HttpStatus.CONFLICT,"Instalação já vinculada. Revogue o vínculo anterior.");
        String hash=hash(pedido.token());var outro=dispositivos.findByTokenHash(hash);
        if(outro.isPresent()&&!outro.get().id.equals(d.id))throw new ResponseStatusException(HttpStatus.CONFLICT,"Token já registrado");
        d.usuarioId=id;d.instalacaoId=pedido.instalacaoId();d.token=pedido.token();d.tokenHash=hash;d.ativo=true;d.atualizadoEm=clock.instant();
        try{dispositivos.saveAndFlush(d);}catch(org.springframework.dao.DataIntegrityViolationException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"Registro concorrente de dispositivo; tente novamente.");}
        return new DispositivoInfo(d.id,d.instalacaoId,d.ativo,d.atualizadoEm);
    }
    @Transactional public void revogar(String uid,String instalacao){Long id=usuario(uid);usuarios.lockById(id).orElseThrow();var d=dispositivos.findByInstalacaoId(instalacao).filter(x->x.usuarioId.equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));d.ativo=false;d.atualizadoEm=clock.instant();}
    private String hash(String valor){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
