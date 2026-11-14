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
    private final ParadaExcursaoRepository paradas;
    private final InscricaoExcursaoRepository inscricoes;
    private final ExecucaoExcursaoRepository execucoes;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ApplicationEventPublisher publisher;
    public ExcursaoService(ExcursaoRepository excursoes,UsuarioRepository usuarios,EntityManager em,Clock clock,ParadaExcursaoRepository paradas,InscricaoExcursaoRepository inscricoes,ExecucaoExcursaoRepository execucoes){this.excursoes=excursoes;this.usuarios=usuarios;this.em=em;this.clock=clock;this.paradas=paradas;this.inscricoes=inscricoes;this.execucoes=execucoes;}
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
    private void editavel(Excursao e){if((e.getEstado()!=EstadoExcursao.RASCUNHO&&e.getEstado()!=EstadoExcursao.PUBLICADA)||!e.getPartidaEm().isAfter(clock.instant())||execucoes.existsByExcursaoId(e.getId()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Excursão encerrada ou já iniciada.");}
    private void versao(Excursao e,Long valor){if(valor==null||e.getVersao()!=valor)throw new ResponseStatusException(HttpStatus.CONFLICT,"Excursão mudou; atualize os dados antes de editar.");}
    @Transactional public ExcursaoDTO criar(String uid,ExcursaoRequest d){
        Usuario u=motorista(uid);validar(d);Excursao e=new Excursao();e.setOrganizador(u);e.setCriadaEm(clock.instant());e.configurar(d);return dto(excursoes.saveAndFlush(e));
    }
    @Transactional public ExcursaoDTO atualizar(Long id,String uid,ExcursaoRequest d){
        Excursao e=propria(id,uid,true);editavel(e);validar(d);versao(e,d.versao());
        long ocupadas=inscricoes.ocupacao(id,clock.instant());
        if(d.capacidade()<ocupadas)throw new ResponseStatusException(HttpStatus.CONFLICT,"Capacidade inferior às vagas reservadas e confirmadas.");
        if(ocupadas>0&&(!Objects.equals(e.getDestinoNome(),d.destinoNome().trim())||!Objects.equals(e.getDestinoLatitude(),d.destinoLatitude())
                ||!Objects.equals(e.getDestinoLongitude(),d.destinoLongitude())||!Objects.equals(e.getOrigemNome(),d.origemNome()==null?null:d.origemNome().trim())
                ||!Objects.equals(e.getOrigemLatitude(),d.origemLatitude())||!Objects.equals(e.getOrigemLongitude(),d.origemLongitude())
                ||!Objects.equals(e.getPartidaEm(),d.partidaEm().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
                ||!Objects.equals(e.getRetornoEm(),d.retornoEm()==null?null:d.retornoEm().truncatedTo(java.time.temporal.ChronoUnit.MICROS))))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Há inscrições ativas: preserve percurso e horários.");
        e.configurar(d);return dto(excursoes.saveAndFlush(e));
    }
    @Transactional public ExcursaoDTO publicar(Long id,String uid,Long versao){
        Excursao e=propria(id,uid,true);editavel(e);versao(e,versao);e.setEstado(EstadoExcursao.PUBLICADA);return dto(excursoes.saveAndFlush(e));
    }
    @Transactional public ExcursaoDTO cancelar(Long id,String uid){
        Excursao e=propria(id,uid,true);if(e.getEstado()==EstadoExcursao.CONCLUIDA)throw new ResponseStatusException(HttpStatus.CONFLICT,"Excursão concluída.");
        if(e.getEstado()==EstadoExcursao.CANCELADA)return dto(e);
        var ativas=execucoes.findByExcursaoIdOrderByIdAsc(id).stream().filter(x->x.getEstado()==EstadoExecucaoExcursao.EM_ANDAMENTO).map(ExecucaoExcursao::getId).toList();
        var avisar=new java.util.ArrayList<>(inscricoes.findByExcursaoIdAndEstadoOrderByIdAsc(id,StatusInscricaoExcursao.SOLICITADA));
        if(ativas.isEmpty())avisar.addAll(inscricoes.findByExcursaoIdAndEstadoOrderByIdAsc(id,StatusInscricaoExcursao.CONFIRMADA));
        if(publisher!=null)ativas.forEach(x->publisher.publishEvent(new EventosDeViagem.Excursao("VALIDAR_FIM",x)));
        e.setEstado(EstadoExcursao.CANCELADA);execucoes.cancelarAtivas(id,clock.instant());inscricoes.cancelarAtivas(id);excursoes.saveAndFlush(e);
        if(publisher!=null){
            ativas.forEach(x->publisher.publishEvent(new EventosDeViagem.Excursao("CANCELADA",x)));
            avisar.forEach(i->publisher.publishEvent(new EventosDeViagem.Direto("VIAGEM_CANCELADA","EXCURSAO",id,null,i.getId(),"EXCURSAO:"+id+":CANCELADA:"+i.getId())));
        }return dto(e);
    }
    @Transactional public ExcursaoDTO configurarColeta(Long id,String uid,ColetaExcursaoRequest d){
        Excursao e=propria(id,uid,true);editavel(e);
        if(d==null||d.modo()==null||d.paradas()==null)throw new IllegalArgumentException("Informe modo e lista de paradas.");
        versao(e,d.versao());int n=d.paradas().size();
        if(inscricoes.ocupacao(id,clock.instant())>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Há inscrições ativas: não altere modo ou paradas.");
        if(n>50||(d.modo()==ModoColetaExcursao.PORTA_A_PORTA&&n!=0)
                ||(d.modo()==ModoColetaExcursao.PARADA_UNICA&&n!=1)||(d.modo()==ModoColetaExcursao.MULTIPLAS_PARADAS&&n<2))
            throw new IllegalArgumentException("Modo incompatível com a quantidade de paradas (máximo 50).");
        var existentes=paradas.findByExcursaoIdOrderByOrdemAscIdAsc(id);
        var porId=new java.util.HashMap<Long,ParadaExcursao>();existentes.forEach(p->porId.put(p.getId(),p));
        var ids=new java.util.HashSet<Long>();
        for(var p:d.paradas()){
            if(p==null||!Boolean.TRUE.equals(p.pontoConfirmado()))throw new IllegalArgumentException("Confirme cada ponto no mapa.");
            texto(p.nome(),120,"nome da parada");ponto(p.latitude(),p.longitude());
            if(p.instrucoes()!=null&&p.instrucoes().length()>500)throw new IllegalArgumentException("Instruções devem ter até 500 caracteres.");
            if(p.id()!=null&&(!porId.containsKey(p.id())||!ids.add(p.id())))throw new IllegalArgumentException("Parada duplicada ou de outra excursão.");
        }
        existentes.forEach(ParadaExcursao::desativar);int ordem=0;
        for(var dpar:d.paradas()){
            ParadaExcursao p=dpar.id()==null?new ParadaExcursao():porId.get(dpar.id());
            p.configurar(e,ordem++,dpar.nome().trim(),dpar.latitude(),dpar.longitude(),dpar.horario(),dpar.instrucoes());paradas.save(p);
        }
        paradas.flush();e.alterarColeta(d.modo());return dto(excursoes.saveAndFlush(e));
    }
    @Transactional(readOnly=true) public ExcursaoDTO consultar(Long id,String uid){return dto(propria(id,uid,false));}
    @Transactional(readOnly=true) public PaginaDTO<ExcursaoDTO> minhas(String uid,int pagina,int tamanho){
        Usuario u=motorista(uid);if(pagina<0||pagina>10000||tamanho<1||tamanho>50)throw new IllegalArgumentException("Paginação inválida.");
        var p=excursoes.findByOrganizadorId(u.getId(),PageRequest.of(pagina,tamanho,Sort.by("id").descending()));
        return new PaginaDTO<>(p.stream().map(this::dto).toList(),p.getTotalElements(),pagina,tamanho,p.getTotalPages());
    }
    private ExcursaoDTO dto(Excursao e){
        var pontos=paradas.findByExcursaoIdOrderByOrdemAscIdAsc(e.getId()).stream().filter(ParadaExcursao::isAtiva)
                .map(p->new ParadaExcursaoDTO(p.getId(),p.getOrdem(),p.getNome(),p.getLatitude(),p.getLongitude(),p.getHorario(),p.getInstrucoes())).toList();
        return new ExcursaoDTO(e.getId(),e.getTitulo(),e.getDestinoNome(),e.getDestinoLatitude(),e.getDestinoLongitude(),e.getOrigemNome(),e.getOrigemLatitude(),e.getOrigemLongitude(),e.getPartidaEm(),e.getRetornoEm(),e.getCapacidade(),e.getEstado(),e.getVersao(),e.getCriadaEm(),e.getModoColeta(),pontos);
    }
}
