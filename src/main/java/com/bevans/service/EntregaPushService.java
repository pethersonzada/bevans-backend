package com.bevans.service;
import com.bevans.model.*;
import com.bevans.repository.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class EntregaPushService {
    public record Pedido(Long entrega,Long notificacao,String token,String hash,String titulo,String mensagem,int tentativa){@Override public String toString(){return "PedidoPush[segredo protegido]";}}
    private final EntregaPushRepository entregas;private final PreferenciaNotificacaoRepository preferencias;
    private final OperacaoViagemRepository operacoes;private final AlunoTurmaRepository vinculos;private final Clock clock;private final EntityManager em;
    private final ContextoProximidadeService proximidade;
    private final AutorizacaoNotificacaoService autorizacao;
    public EntregaPushService(EntregaPushRepository e,PreferenciaNotificacaoRepository p,OperacaoViagemRepository o,AlunoTurmaRepository v,Clock c,EntityManager em,ContextoProximidadeService proximidade,AutorizacaoNotificacaoService autorizacao){entregas=e;preferencias=p;operacoes=o;vinculos=v;clock=c;this.em=em;this.proximidade=proximidade;this.autorizacao=autorizacao;}
    @Transactional public Pedido reservar(){
        var lista=entregas.pendentes(clock.instant(),PageRequest.of(0,20));
        for(var e:lista){em.refresh(e);
        if(!java.util.Set.of("PENDENTE","ENVIANDO").contains(e.estado)||e.proximaTentativa.isAfter(clock.instant()))continue;
        var d=e.dispositivo;em.refresh(d);
        if(!autorizada(e)){e.estado="CANCELADA";e.resultado="SEM_AUTORIZACAO";continue;}
        if(e.tentativas>=5){e.estado="FALHA";e.resultado="LIMITE_TENTATIVAS";continue;}
        e.estado="ENVIANDO";e.tentativas++;e.proximaTentativa=clock.instant().plusSeconds(120);
        return new Pedido(e.id,e.notificacao.id,d.token,d.tokenHash,e.notificacao.titulo,e.notificacao.mensagem,e.tentativas);
        }return null;
    }
    private boolean autorizada(EntregaPush e){
        var d=e.dispositivo;var n=e.notificacao;
        if(!d.ativo||n.usuarioId==null||!n.usuarioId.equals(d.usuarioId)||!preferencias.findById(d.usuarioId).map(p->p.pushOperacional).orElse(true))return false;
        if(ContextoProximidadeService.TIPOS.contains(n.tipo)&&!proximidade.valido(n.operacaoId,n.registroId,n.tipo,n.criadaEm))return false;
        if(n.operacaoId==null)return autorizacao.direto(n.contextoTipo,n.contextoId,n.tipo,n.usuarioId,null);
        var op=operacoes.findById(n.operacaoId).orElseThrow();
        return op.motorista.getId().equals(d.usuarioId)||(op.viagem!=null&&vinculos.existsByTurmaIdAndAlunoIdAndStatus(op.viagem.getTurma().getId(),d.usuarioId,"APROVADO"));
    }
    @Transactional public void concluir(Pedido p,PushGateway.Resultado resultado){
        var e=entregas.lockById(p.entrega()).orElseThrow();em.refresh(e);
        if(!"ENVIANDO".equals(e.estado)||e.tentativas!=p.tentativa())return;
        em.refresh(e.dispositivo);
        if(!e.dispositivo.tokenHash.equals(p.hash())){e.estado="PENDENTE";e.proximaTentativa=clock.instant();return;}
        e.resultado=resultado.name();
        switch(resultado){
            case ENVIADO->e.estado="ENVIADA";
            case TOKEN_INVALIDO->{e.estado="FALHA";e.dispositivo.ativo=false;e.dispositivo.atualizadoEm=clock.instant();}
            case PERMANENTE->e.estado="FALHA";
            case TRANSITORIO->{e.estado=e.tentativas>=5?"FALHA":"PENDENTE";e.proximaTentativa=clock.instant().plusSeconds(30L*(1L<<e.tentativas));}
            case INDISPONIVEL->{e.estado="PENDENTE";e.tentativas--;e.proximaTentativa=clock.instant().plusSeconds(3600);}
        }
    }
}
