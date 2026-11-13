package com.bevans.service;

import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.*;
import jakarta.persistence.*;
import java.time.Clock;
import java.util.Objects;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExcursaoService {
    private final ExcursaoRepository excursoes;
    private final UsuarioRepository usuarios;
    private final EntityManager em;
    private final Clock clock;
    public ExcursaoService(ExcursaoRepository excursoes,UsuarioRepository usuarios,EntityManager em,Clock clock){this.excursoes=excursoes;this.usuarios=usuarios;this.em=em;this.clock=clock;}
    private Usuario motorista(String uid){
        Usuario u=usuarios.findByFirebaseUid(uid).orElseThrow(()->new AccessDeniedException("Perfil não encontrado."));
        if(!"MOTORISTA".equals(u.getTipo()))throw new AccessDeniedException("Somente motoristas gerenciam excursões.");return u;
    }
    private Excursao propria(Long id,String uid,boolean lock){
        Usuario u=motorista(uid);
        Excursao e=(lock?excursoes.lockById(id):excursoes.findById(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Excursão não encontrada."));
        if(lock)em.refresh(e,LockModeType.PESSIMISTIC_WRITE);
        if(!Objects.equals(u.getId(),e.getOrganizador().getId()))throw new AccessDeniedException("Excursão de outro organizador.");return e;
    }
    private void texto(String s,int limite,String nome){if(s==null||s.isBlank()||s.trim().length()>limite)throw new IllegalArgumentException("Informe "+nome+" válido.");}
    private void ponto(Double lat,Double lon){if(lat==null||lon==null||!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180||(lat==0&&lon==0))throw new IllegalArgumentException("Coordenadas inválidas.");}
    private void validar(ExcursaoRequest d){
        if(d==null)throw new IllegalArgumentException("Informe a excursão.");
        texto(d.titulo(),120,"título");texto(d.destinoNome(),255,"destino");ponto(d.destinoLatitude(),d.destinoLongitude());
        if(d.origemNome()!=null||d.origemLatitude()!=null||d.origemLongitude()!=null){texto(d.origemNome(),255,"origem");ponto(d.origemLatitude(),d.origemLongitude());}
        if(d.capacidade()==null||d.capacidade()<=0)throw new IllegalArgumentException("Capacidade deve ser inteira e positiva.");
        if(d.partidaEm()==null||!d.partidaEm().isAfter(clock.instant()))throw new IllegalArgumentException("Partida deve estar no futuro.");
        if(d.retornoEm()!=null&&!d.retornoEm().isAfter(d.partidaEm()))throw new IllegalArgumentException("Retorno deve ser posterior à partida.");
    }
    private void editavel(Excursao e){if((e.getEstado()!=EstadoExcursao.RASCUNHO&&e.getEstado()!=EstadoExcursao.PUBLICADA)||!e.getPartidaEm().isAfter(clock.instant()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Excursão encerrada ou já iniciada.");}
    private void versao(Excursao e,Long valor){if(valor==null||e.getVersao()!=valor)throw new ResponseStatusException(HttpStatus.CONFLICT,"Excursão mudou; atualize os dados antes de editar.");}
    @Transactional public ExcursaoDTO criar(String uid,ExcursaoRequest d){
        Usuario u=motorista(uid);validar(d);Excursao e=new Excursao();e.setOrganizador(u);e.setCriadaEm(clock.instant());e.configurar(d);return dto(excursoes.saveAndFlush(e));
    }
    @Transactional public ExcursaoDTO atualizar(Long id,String uid,ExcursaoRequest d){
        Excursao e=propria(id,uid,true);editavel(e);validar(d);versao(e,d.versao());e.configurar(d);return dto(excursoes.saveAndFlush(e));
    }
    @Transactional public ExcursaoDTO publicar(Long id,String uid,Long versao){
        Excursao e=propria(id,uid,true);editavel(e);versao(e,versao);e.setEstado(EstadoExcursao.PUBLICADA);return dto(excursoes.saveAndFlush(e));
    }
    @Transactional public ExcursaoDTO cancelar(Long id,String uid){
        Excursao e=propria(id,uid,true);if(e.getEstado()==EstadoExcursao.CONCLUIDA)throw new ResponseStatusException(HttpStatus.CONFLICT,"Excursão concluída.");
        e.setEstado(EstadoExcursao.CANCELADA);return dto(excursoes.saveAndFlush(e));
    }
    @Transactional(readOnly=true) public ExcursaoDTO consultar(Long id,String uid){return dto(propria(id,uid,false));}
    @Transactional(readOnly=true) public PaginaDTO<ExcursaoDTO> minhas(String uid,int pagina,int tamanho){
        Usuario u=motorista(uid);if(pagina<0||pagina>10000||tamanho<1||tamanho>50)throw new IllegalArgumentException("Paginação inválida.");
        var p=excursoes.findByOrganizadorId(u.getId(),PageRequest.of(pagina,tamanho,Sort.by("id").descending()));
        return new PaginaDTO<>(p.stream().map(this::dto).toList(),p.getTotalElements(),pagina,tamanho,p.getTotalPages());
    }
    private ExcursaoDTO dto(Excursao e){return new ExcursaoDTO(e.getId(),e.getTitulo(),e.getDestinoNome(),e.getDestinoLatitude(),e.getDestinoLongitude(),e.getOrigemNome(),e.getOrigemLatitude(),e.getOrigemLongitude(),e.getPartidaEm(),e.getRetornoEm(),e.getCapacidade(),e.getEstado(),e.getVersao(),e.getCriadaEm());}
}
