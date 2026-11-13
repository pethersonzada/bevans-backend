package com.bevans.service;
import com.bevans.model.*;
import com.bevans.dto.*;
import com.bevans.repository.*;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FavoritoService {
    private final FavoritoRepository favoritos;
    private final UsuarioRepository usuarios;
    private final RotaPublicaRepository rotas;
    private final CatalogoService catalogo;
    private final EntityManager em;
    public FavoritoService(FavoritoRepository favoritos,UsuarioRepository usuarios,RotaPublicaRepository rotas,CatalogoService catalogo,EntityManager em){
        this.favoritos=favoritos;this.usuarios=usuarios;this.rotas=rotas;this.catalogo=catalogo;this.em=em;
    }
    private Usuario titular(Long usuarioId,boolean lock) {
        Usuario u=(lock?usuarios.lockById(usuarioId):usuarios.findById(usuarioId)).orElseThrow(()->new AccessDeniedException("Sessão inválida."));
        if(lock)em.refresh(u,LockModeType.PESSIMISTIC_WRITE);
        if(!"PASSAGEIRO".equals(u.getTipo()))throw new AccessDeniedException("Somente passageiros gerenciam favoritos.");return u;
    }
    @Transactional
    public void favoritar(Long usuarioId,Long rotaId) {
        Usuario u=titular(usuarioId,true);
        RotaPublica r=rotas.findById(rotaId).filter(v->v.getPublicada()&&v.getAtiva()&&v.getTurma()!=null&&!v.getTurma().isExcluido())
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Rota indisponível para favoritar."));
        FavoritoRota f=favoritos.findByUsuarioIdAndRotaId(usuarioId,rotaId).orElse(null);
        if(f==null){f=new FavoritoRota();f.setUsuario(u);f.setRota(r);}else em.refresh(f);
        if(!f.isAtivo()){f.setAtivo(true);favoritos.saveAndFlush(f);}
    }
    @Transactional
    public void remover(Long usuarioId,Long rotaId) {
        titular(usuarioId,true);
        favoritos.findByUsuarioIdAndRotaId(usuarioId,rotaId).ifPresent(f->{em.refresh(f);if(f.isAtivo()){f.setAtivo(false);favoritos.saveAndFlush(f);}});
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PaginaDTO<CatalogoRotaDTO> listar(Long usuarioId,int pagina,int tamanho) {
        titular(usuarioId,false);
        return catalogo.buscar(new FiltroCatalogo(null,null,null,null,null,null,null,null,false),pagina,tamanho,usuarioId,true);
    }
    @Transactional(readOnly=true)
    public boolean favorita(Long usuarioId,Long rotaId){return usuarioId!=null&&favoritos.existsByUsuarioIdAndRotaIdAndAtivoTrue(usuarioId,rotaId);}
}
