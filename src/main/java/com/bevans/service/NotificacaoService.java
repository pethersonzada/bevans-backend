package com.bevans.service;

import com.bevans.dto.NotificacaoDTO;
import com.bevans.model.*;
import com.bevans.repository.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** O evento fica na mesma transação do embarque; a distribuição ocorre em outra transação. */
@Service
public class NotificacaoService {
    private final NotificacaoRepository notificacoes;
    private final OutboxNotificacaoRepository outbox;
    private final OperacaoViagemRepository operacoes;
    private final RegistroOperacionalRepository registros;
    private final UsuarioRepository usuarios;
    private final AlunoTurmaRepository vinculos;
    private final EntityManager em;
    private final Clock clock;
    private final DispositivoNotificacaoRepository dispositivos;
    private final PreferenciaNotificacaoRepository preferencias;
    private final EntregaPushRepository entregas;
    public NotificacaoService(NotificacaoRepository n,OutboxNotificacaoRepository o,OperacaoViagemRepository op,
            RegistroOperacionalRepository r,UsuarioRepository u,AlunoTurmaRepository v,EntityManager em,Clock clock,
            DispositivoNotificacaoRepository dispositivos,PreferenciaNotificacaoRepository preferencias,EntregaPushRepository entregas){
        notificacoes=n;outbox=o;operacoes=op;registros=r;usuarios=u;vinculos=v;this.em=em;this.clock=clock;
        this.dispositivos=dispositivos;this.preferencias=preferencias;this.entregas=entregas;
    }
    @EventListener @Transactional
    public void registrar(EventosDeViagem.Operacao evento){
        if(outbox.existsByChave(evento.chave()))return;
        OutboxNotificacao o=new OutboxNotificacao();o.chave=evento.chave();o.tipo=evento.tipo();
        o.operacaoId=evento.operacaoId();o.registroId=evento.registroId();
        if(o.registroId!=null)o.origem=registros.findById(o.registroId).orElseThrow().origem;
        o.criadaEm=clock.instant();o.proximaTentativa=o.criadaEm;outbox.save(o);
    }
    @EventListener @Transactional public void registrar(EventosDeViagem.Direto evento){
        if(outbox.existsByChave(evento.chave()))return;
        if((evento.usuarioId()==null)==(evento.inscricaoId()==null))throw new IllegalArgumentException("Destinatário único obrigatório");
        var o=new OutboxNotificacao();o.chave=evento.chave();o.tipo=evento.tipo();o.contextoTipo=evento.contextoTipo();o.contextoId=evento.contextoId();
        o.usuarioId=evento.usuarioId();o.inscricaoId=evento.inscricaoId();o.criadaEm=clock.instant();o.proximaTentativa=o.criadaEm;outbox.save(o);
    }
    /** Bloqueio pessimista e distribuição atômica: falha reverte tanto inbox quanto consumo. */
    @Transactional
    public void distribuir(Long eventoId){
        Instant agora=clock.instant();
        OutboxNotificacao evento=outbox.lockById(eventoId).orElseThrow();em.refresh(evento);
        if(!"PROCESSANDO".equals(evento.estado))return;
        if(evento.operacaoId==null){salvar(evento,null,evento.usuarioId,evento.inscricaoId,agora);evento.estado="PROCESSADA";return;}
        OperacaoViagem op=operacoes.findById(evento.operacaoId).orElseThrow();
        var participantes=registros.findByOperacaoIdOrderByOrdemAscIdAsc(op.id);
        if(evento.registroId!=null){
            var registro=participantes.stream().filter(r->r.id.equals(evento.registroId)).findFirst().orElseThrow();
            destinatario(evento,op,registro,agora);
            if(!"MOTORISTA".equals(evento.origem))salvar(evento,op,op.motorista.getId(),null,agora);
        }else{
            for(var registro:participantes)destinatario(evento,op,registro,agora);
            salvar(evento,op,op.motorista.getId(),null,agora);
        }
        evento.estado="PROCESSADA";
    }
    private void destinatario(OutboxNotificacao e,OperacaoViagem op,RegistroOperacional r,Instant agora){
        if(r.aluno!=null){
            if(op.viagem==null||!vinculos.existsByTurmaIdAndAlunoIdAndStatus(op.viagem.getTurma().getId(),r.aluno.getId(),"APROVADO"))return;
            salvar(e,op,r.aluno.getId(),null,agora);
        }else if(r.inscricao!=null&&(r.inscricao.getEstado()==StatusInscricaoExcursao.CONFIRMADA||"VIAGEM_CANCELADA".equals(e.tipo))){
            salvar(e,op,null,r.inscricao.getId(),agora);
        }
    }
    private void salvar(OutboxNotificacao e,OperacaoViagem op,Long usuario,Long inscricao,Instant agora){
        String chave=e.chave+(usuario!=null?":U:"+usuario:":I:"+inscricao);
        if(notificacoes.existsByChave(chave))return;
        Notificacao n=new Notificacao();n.chave=chave;n.tipo=e.tipo;n.usuarioId=usuario;n.inscricaoId=inscricao;
        n.operacaoId=op==null?null:op.id;n.registroId=e.registroId;n.contextoTipo=op==null?e.contextoTipo:op.tipo.name();n.contextoId=op==null?e.contextoId:op.referenciaId;
        n.titulo="Atualização da viagem";n.mensagem=mensagem(e.tipo);n.criadaEm=agora;notificacoes.save(n);
        if(usuario!=null&&preferencias.findById(usuario).map(p->p.pushOperacional).orElse(true)){
            for(var dispositivo:dispositivos.findByUsuarioIdAndAtivoTrue(usuario)){
                var entrega=new EntregaPush();entrega.notificacao=n;entrega.dispositivo=dispositivo;entrega.proximaTentativa=agora;entregas.save(entrega);
            }
        }
    }
    private String mensagem(String tipo){return switch(tipo){
        case "VIAGEM_INICIADA"->"A viagem foi iniciada.";
        case "VIAGEM_FINALIZADA"->"A viagem foi finalizada.";
        case "VIAGEM_CANCELADA"->"A viagem foi cancelada.";
        case "EMBARQUE_CONFIRMADO"->"O embarque foi confirmado.";
        case "DESEMBARQUE_CONFIRMADO"->"O desembarque foi confirmado.";
        case "NAO_EMBARCOU"->"Foi registrada uma ausência de embarque.";
        case "SOLICITACAO_APROVADA"->"Sua solicitação foi aprovada.";
        case "SOLICITACAO_RECUSADA"->"Sua solicitação foi recusada.";
        case "NOVA_INSCRICAO"->"Uma nova inscrição aguarda análise.";
        case "INSCRICAO_CONFIRMADA"->"Sua inscrição foi confirmada.";
        case "INSCRICAO_CANCELADA"->"Uma inscrição foi cancelada.";
        default->"O estado operacional foi atualizado. Consulte os detalhes autorizados no aplicativo.";
    };}
    private Long usuario(String uid){return usuarios.findByFirebaseUid(uid).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN)).getId();}
    @Transactional(readOnly=true)
    public Page<NotificacaoDTO> listar(String uid,int pagina,int tamanho){
        if(pagina<0||tamanho<1||tamanho>50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Paginação inválida");
        return notificacoes.findByUsuarioId(usuario(uid),PageRequest.of(pagina,tamanho,Sort.by(Sort.Direction.DESC,"id"))).map(this::dto);
    }
    @Transactional(readOnly=true) public long naoLidas(String uid){return notificacoes.countByUsuarioIdAndLidaEmIsNull(usuario(uid));}
    @Transactional public void ler(String uid,Long id){var n=notificacoes.findByIdAndUsuarioId(id,usuario(uid)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(n.lidaEm==null)n.lidaEm=clock.instant();}
    @Transactional public void lerTodas(String uid){notificacoes.lerTodas(usuario(uid),clock.instant());}
    private NotificacaoDTO dto(Notificacao n){return new NotificacaoDTO(n.id,n.tipo,n.contextoTipo,n.contextoId,n.titulo,n.mensagem,n.criadaEm,n.lidaEm);}
}
