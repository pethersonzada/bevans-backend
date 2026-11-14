package com.bevans.service;
import com.bevans.model.*;
import com.bevans.repository.*;
import com.bevans.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.*;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ExecucaoExcursaoService {
    private final ExecucaoExcursaoRepository execucoes;private final ExcursaoRepository excursoes;
    private final ExcursaoService administracao;private final RoteiroExcursaoService roteiros;private final EntityManager em;private final Clock clock;
    // Snapshot interno versionado, sem desserialização polimórfica nem datas nos pontos.
    private final ObjectMapper json=new ObjectMapper();
    public ExecucaoExcursaoService(ExecucaoExcursaoRepository x,ExcursaoRepository e,ExcursaoService a,RoteiroExcursaoService r,EntityManager em,Clock c){execucoes=x;excursoes=e;administracao=a;roteiros=r;this.em=em;clock=c;}
    private ResponseStatusException conflito(String texto){return new ResponseStatusException(HttpStatus.CONFLICT,texto);}
    private Excursao lock(Long id,String uid){
        administracao.consultar(id,uid);var e=excursoes.lockById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Excursão não encontrada."));
        em.refresh(e,LockModeType.PESSIMISTIC_WRITE);return e;
    }
    @Transactional public ExecucaoExcursaoDTO iniciar(Long id,String uid,String sentido,boolean otimizar){
        var e=lock(id,uid);
        if(!"IDA".equals(sentido)&&!"VOLTA".equals(sentido))throw new IllegalArgumentException("Informe IDA ou VOLTA.");
        var existente=execucoes.findByExcursaoIdAndSentido(id,sentido).orElse(null);
        if(existente!=null){em.refresh(existente);if(existente.getEstado()==EstadoExecucaoExcursao.EM_ANDAMENTO)return dto(existente);throw conflito("Este sentido já foi executado ou cancelado.");}
        if(e.getEstado()!=EstadoExcursao.PUBLICADA)throw conflito("Excursão não está disponível para iniciar.");
        if(execucoes.findByExcursaoIdOrderByIdAsc(id).stream().anyMatch(x->x.getEstado()==EstadoExecucaoExcursao.EM_ANDAMENTO))throw conflito("Outra execução está em andamento.");
        if("VOLTA".equals(sentido)&&execucoes.findByExcursaoIdAndSentido(id,"IDA").filter(x->x.getEstado()==EstadoExecucaoExcursao.FINALIZADA).isEmpty())throw conflito("Finalize a ida antes de iniciar a volta.");
        var pontos=roteiros.paraInicio(id,uid,sentido,otimizar);String snapshot;
        try{snapshot=json.writeValueAsString(pontos);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Não foi possível registrar o roteiro.");}
        var x=new ExecucaoExcursao();x.criar(e,sentido,snapshot,clock.instant());return dto(execucoes.saveAndFlush(x));
    }
    @Transactional public ExecucaoExcursaoDTO finalizar(Long id,Long execucaoId,String uid){
        var e=lock(id,uid);var x=execucoes.findById(execucaoId).filter(v->v.getExcursao().getId().equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Execução não encontrada."));em.refresh(x);
        if(x.getEstado()==EstadoExecucaoExcursao.FINALIZADA)return dto(x);
        if(x.getEstado()!=EstadoExecucaoExcursao.EM_ANDAMENTO||e.getEstado()==EstadoExcursao.CANCELADA)throw conflito("Execução cancelada.");
        x.finalizar(clock.instant());
        if("VOLTA".equals(x.getSentido())||e.getRetornoEm()==null){e.setEstado(EstadoExcursao.CONCLUIDA);excursoes.save(e);}
        return dto(execucoes.saveAndFlush(x));
    }
    @Transactional(readOnly=true) public List<ExecucaoExcursaoDTO> listar(Long id,String uid){administracao.consultar(id,uid);return execucoes.findByExcursaoIdOrderByIdAsc(id).stream().map(this::dto).toList();}
    private ExecucaoExcursaoDTO dto(ExecucaoExcursao x){
        try{
            if(x.getSnapshotVersao()!=1)throw new IllegalStateException("Versão de snapshot não suportada");
            List<PontoRoteiroExcursaoDTO> pontos=json.readValue(x.getRoteiroJson(),new TypeReference<List<PontoRoteiroExcursaoDTO>>(){});
            return new ExecucaoExcursaoDTO(x.getId(),x.getExcursao().getId(),x.getSentido(),x.getEstado(),x.getIniciadaEm(),x.getFinalizadaEm(),x.getVersao(),List.copyOf(pontos));
        }catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Roteiro da execução indisponível.");}
    }
}
