package com.bevans.service;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.InscricaoExcursaoRepository;
import com.bevans.repository.ExecucaoExcursaoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@Service
public class RoteiroExcursaoService {
    private final ExcursaoService excursoes;private final InscricaoExcursaoRepository inscricoes;private final Clock clock;
    private final ExecucaoExcursaoRepository execucoes;
    public RoteiroExcursaoService(ExcursaoService e,InscricaoExcursaoRepository i,Clock c,ExecucaoExcursaoRepository x){excursoes=e;inscricoes=i;clock=c;execucoes=x;}
    private ResponseStatusException conflito(String causa){return new com.bevans.exception.ConflitoOperacionalException("ROTEIRO_INCONSISTENTE",causa);}
    private void coordenadas(Double lat,Double lon){if(lat==null||lon==null||!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180||(lat==0&&lon==0))throw conflito("Há ponto sem coordenadas válidas no roteiro da excursão.");}
    private List<PontoRoteiroExcursaoDTO> roteiroSalvo(ExecucaoExcursao execucao){
        if(execucao.getSnapshotVersao()!=1)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Versão de roteiro da execução não suportada.");
        try{
            var pontos=new ObjectMapper().readValue(execucao.getRoteiroJson(),new TypeReference<List<PontoRoteiroExcursaoDTO>>(){});
            if(pontos==null||pontos.isEmpty())throw new IllegalArgumentException("Snapshot de roteiro vazio.");
            return List.copyOf(pontos);
        }catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Roteiro salvo da execução indisponível.");}
    }
    private PontoRoteiroExcursaoDTO extremo(String tipo,String nome,double lat,double lon){return new PontoRoteiroExcursaoDTO(tipo,null,nome,lat,lon,List.of());}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<PontoRoteiroExcursaoDTO> planejamento(Long id,String uid,String sentido,boolean otimizar){
        // Autorização antes da leitura de um snapshot que contém pontos privados.
        excursoes.consultar(id,uid);
        if(!"IDA".equals(sentido)&&!"VOLTA".equals(sentido))throw new IllegalArgumentException("Informe IDA ou VOLTA.");
        var existente=execucoes.findByExcursaoIdAndSentido(id,sentido);
        // Quando a excursão já iniciou, a prévia deve mostrar exatamente o
        // roteiro imutável da execução, inclusive após ela ser finalizada.
        if(existente.isPresent())return roteiroSalvo(existente.get());
        return calcular(id,uid,sentido,otimizar,true);
    }
    @Transactional(readOnly=true)
    public List<PontoRoteiroExcursaoDTO> paraInicio(Long id,String uid,String sentido,boolean otimizar){return calcular(id,uid,sentido,otimizar,false);}
    private List<PontoRoteiroExcursaoDTO> calcular(Long id,String uid,String sentido,boolean otimizar,boolean previa){
        ExcursaoDTO e=excursoes.consultar(id,uid); // Valida perfil e dono no serviço, inclusive chamadas internas.
        if(!"IDA".equals(sentido)&&!"VOLTA".equals(sentido))throw new IllegalArgumentException("Informe IDA ou VOLTA.");
        boolean volta="VOLTA".equals(sentido);
        if(volta&&(e.retornoEm()==null||e.origemLatitude()==null))throw new IllegalArgumentException("Retorno exige horário de volta e origem operacional configurados.");
        if(!previa&&e.estado()!=EstadoExcursao.PUBLICADA)throw new com.bevans.exception.ConflitoOperacionalException("ESTADO_INVALIDO","Publique a excursão antes de iniciar a execução.");
        if(previa&&(e.estado()==EstadoExcursao.CANCELADA||e.estado()==EstadoExcursao.CONCLUIDA))throw new com.bevans.exception.ConflitoOperacionalException("ESTADO_INVALIDO","Excursão encerrada. Consulte o roteiro da execução anterior.");
        // Uma prévia de leitura continua útil após o horário previsto.
        // A regra de partida é tratada por ExecucaoExcursaoService.
        var confirmadas=inscricoes.findByExcursaoIdAndEstadoOrderByIdAsc(id,StatusInscricaoExcursao.CONFIRMADA);
        var coletas=new ArrayList<PontoRoteiroExcursaoDTO>();
        if(e.modoColeta()==ModoColetaExcursao.PORTA_A_PORTA){
            for(var i:confirmadas){coordenadas(i.getLatitude(),i.getLongitude());coletas.add(new PontoRoteiroExcursaoDTO("COLETA",null,i.getNomePonto(),i.getLatitude(),i.getLongitude(),List.of(new PontoRoteiroExcursaoDTO.Participante(i.getId(),i.getNome()))));}
        }else{
            var porParada=new HashMap<Long,List<PontoRoteiroExcursaoDTO.Participante>>();
            for(var i:confirmadas){
                if(i.getParada()==null||e.paradas().stream().noneMatch(p->p.id().equals(i.getParada().getId())))throw conflito("Há participante confirmado sem parada ativa nesta excursão.");
                porParada.computeIfAbsent(i.getParada().getId(),v->new ArrayList<>()).add(new PontoRoteiroExcursaoDTO.Participante(i.getId(),i.getNome()));
            }
            for(var p:e.paradas()){coordenadas(p.latitude(),p.longitude());coletas.add(new PontoRoteiroExcursaoDTO("COLETA",p.id(),p.nome(),p.latitude(),p.longitude(),List.copyOf(porParada.getOrDefault(p.id(),List.of()))));}
        }
        if(volta){Collections.reverse(coletas);coletas.replaceAll(p->new PontoRoteiroExcursaoDTO("DESEMBARQUE",p.paradaId(),p.nome(),p.latitude(),p.longitude(),p.participantes()));}
        var pontos=new ArrayList<PontoRoteiroExcursaoDTO>();
        coordenadas(e.destinoLatitude(),e.destinoLongitude());
        if(e.origemLatitude()!=null)coordenadas(e.origemLatitude(),e.origemLongitude());
        if(volta)pontos.add(extremo("ORIGEM",e.destinoNome(),e.destinoLatitude(),e.destinoLongitude()));
        else if(e.origemLatitude()!=null)pontos.add(extremo("ORIGEM",e.origemNome(),e.origemLatitude(),e.origemLongitude()));
        pontos.addAll(coletas);
        if(volta)pontos.add(extremo("DESTINO",e.origemNome(),e.origemLatitude(),e.origemLongitude()));
        else pontos.add(extremo("DESTINO",e.destinoNome(),e.destinoLatitude(),e.destinoLongitude()));
        if(!otimizar)return List.copyOf(pontos);
        if(!volta&&e.origemLatitude()==null)throw new IllegalArgumentException("Configure origem operacional antes de otimizar.");
        // Adaptador interno do algoritmo existente; não cria/consulta ParadaPublica.
        var entrada=new ArrayList<PontoPublicoDTO>();for(int i=0;i<pontos.size();i++){var p=pontos.get(i);entrada.add(new PontoPublicoDTO((long)i,p.nome(),p.latitude(),p.longitude()));}
        return OtimizadorParadas.ordenar(entrada).stream().map(p->pontos.get(Math.toIntExact(p.paradaId()))).toList();
    }
}
