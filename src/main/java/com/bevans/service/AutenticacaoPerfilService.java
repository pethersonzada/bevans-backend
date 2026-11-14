package com.bevans.service;
import com.bevans.model.Usuario;
import com.bevans.repository.UsuarioRepository;
import com.bevans.exception.RecursoNaoEncontradoException;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AutenticacaoPerfilService {
    private final UsuarioRepository usuarios;private final EntityManager em;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.bevans.repository.ExclusaoContaRepository exclusoes;
    public AutenticacaoPerfilService(UsuarioRepository u,EntityManager em){usuarios=u;this.em=em;}
    @Transactional public Map<String,String> login(String uid,String emailInformado,String emailToken,boolean emailVerificado){
        if(uid==null||uid.isBlank()||emailToken==null||emailInformado==null||!emailToken.equalsIgnoreCase(emailInformado.trim()))throw new AccessDeniedException("Identidade não corresponde ao token Firebase");
        if(exclusoes!=null&&exclusoes.existsByFirebaseUid(uid))throw new AccessDeniedException("Conta com exclusão registrada");
        var u=usuarios.findByFirebaseUid(uid).orElseGet(()->usuarios.findByEmail(emailToken).orElseThrow(()->new RecursoNaoEncontradoException("Perfil não encontrado")));
        conferir(u,uid,emailVerificado);u=usuarios.lockById(u.getId()).orElseThrow(()->new RecursoNaoEncontradoException("Perfil não encontrado"));em.refresh(u,LockModeType.PESSIMISTIC_WRITE);conferir(u,uid,emailVerificado);
        if(u.getFirebaseUid()==null){u.setFirebaseUid(uid);usuarios.saveAndFlush(u);}
        return Map.of("id",u.getId().toString(),"tipo",u.getTipo(),"nome",u.getNome()==null?"":u.getNome(),"endereco",u.getEnderecoCompleto()==null?"":u.getEnderecoCompleto());
    }
    private void conferir(Usuario u,String uid,boolean verificado){
        if(u.getFirebaseUid()!=null&&!Objects.equals(u.getFirebaseUid(),uid))throw new AccessDeniedException("Perfil pertence a outra identidade");
        if(u.getFirebaseUid()==null&&!verificado)throw new AccessDeniedException("Associação de perfil legado exige email verificado pelo Firebase");
    }
}
