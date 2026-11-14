package com.bevans.service;
import com.bevans.model.*;
import com.bevans.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** Verifica a próxima ordem operacional; distância em linha reta, sem ETA. */
@Service
public class ContextoProximidadeService {
    public static final Set<String> TIPOS=Set.of("VAN_APROXIMANDO","VOCE_E_PROXIMO","VAN_CHEGOU");
    private final OperacaoViagemRepository operacoes;private final RegistroOperacionalRepository registros;
    private final PosicaoViagemRepository viagens;private final PosicaoExecucaoExcursaoRepository excursoes;
    private final AlunoTurmaRepository vinculos;private final Clock clock;
    private final TurmaRepository turmas;private final ExcursaoRepository passeios;private final jakarta.persistence.EntityManager em;
    public ContextoProximidadeService(OperacaoViagemRepository o,RegistroOperacionalRepository r,PosicaoViagemRepository v,PosicaoExecucaoExcursaoRepository e,AlunoTurmaRepository a,Clock c,TurmaRepository turmas,ExcursaoRepository passeios,jakarta.persistence.EntityManager em){operacoes=o;registros=r;viagens=v;excursoes=e;vinculos=a;clock=c;this.turmas=turmas;this.passeios=passeios;this.em=em;}
    private AmostraGps gps(OperacaoViagem o){
        if(o.tipo==TipoOperacao.VIAGEM){if(!"EM_ANDAMENTO".equals(o.viagem.getStatus()))return null;return viagens.findById(o.referenciaId).orElse(null);}
        if(o.execucao.getEstado()!=EstadoExecucaoExcursao.EM_ANDAMENTO||o.execucao.getExcursao().getEstado()==EstadoExcursao.CANCELADA)return null;
        return excursoes.findById(o.referenciaId).orElse(null);
    }
    public boolean autorizado(OperacaoViagem o,RegistroOperacional r){return r.aluno!=null?
        o.viagem!=null&&vinculos.existsByTurmaIdAndAlunoIdAndStatus(o.viagem.getTurma().getId(),r.aluno.getId(),"APROVADO"):
        r.inscricao!=null&&r.inscricao.getEstado()==StatusInscricaoExcursao.CONFIRMADA;}
    @Transactional(readOnly=true) public Map<Long,String> alertas(Long operacaoId){
        var o=operacoes.findById(operacaoId).orElseThrow();var p=gps(o);
        if(!"ATUALIZADA".equals(RegrasGps.estado(true,p,clock))||p.getPrecisao()==null||p.getPrecisao()>50)return Map.of();
        var esperados=registros.findByOperacaoIdOrderByOrdemAscIdAsc(o.id).stream().filter(r->r.estado==EstadoOperacional.AGUARDANDO&&autorizado(o,r)).toList();
        if(esperados.isEmpty())return Map.of();int ordem=esperados.getFirst().ordem;var resultado=new LinkedHashMap<Long,String>();
        for(var r:esperados){if(r.ordem!=ordem)break;double distancia=metros(p.getLatitude(),p.getLongitude(),r.latitude,r.longitude)+p.getPrecisao();
            String tipo=distancia<=50?"VAN_CHEGOU":distancia<=200?"VOCE_E_PROXIMO":distancia<=500?"VAN_APROXIMANDO":null;
            if(tipo!=null)resultado.put(r.id,tipo);
        }return resultado;
    }
    @Transactional public boolean valido(Long operacao,Long registro,String tipo,Instant criado){
        if(criado==null||!clock.instant().isBefore(criado.plusSeconds(30)))return false;
        var o=operacoes.findById(operacao).orElseThrow();
        if(o.viagem!=null){var t=turmas.lockById(o.viagem.getTurma().getId()).orElseThrow();em.refresh(t);em.refresh(o.viagem);}
        else{var e=passeios.lockById(o.execucao.getExcursao().getId()).orElseThrow();em.refresh(e);em.refresh(o.execucao);}
        return tipo.equals(alertas(operacao).get(registro));
    }
    static double metros(double lat1,double lon1,double lat2,double lon2){double a=Math.pow(Math.sin(Math.toRadians(lat2-lat1)/2),2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.pow(Math.sin(Math.toRadians(lon2-lon1)/2),2);return 6371000*2*Math.asin(Math.sqrt(Math.min(1,a)));}
}
