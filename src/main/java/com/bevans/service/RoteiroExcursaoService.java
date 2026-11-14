package com.bevans.service;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.InscricaoExcursaoRepository;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@Service
public class RoteiroExcursaoService {
    private final ExcursaoService excursoes;private final InscricaoExcursaoRepository inscricoes;private final Clock clock;
    public RoteiroExcursaoService(ExcursaoService e,InscricaoExcursaoRepository i,Clock c){excursoes=e;inscricoes=i;clock=c;}
    private ResponseStatusException conflito(){return new ResponseStatusException(HttpStatus.CONFLICT,"Planejamento indisponível ou inconsistente; revise a excursão.");}
    private PontoRoteiroExcursaoDTO extremo(String tipo,String nome,double lat,double lon){return new PontoRoteiroExcursaoDTO(tipo,null,nome,lat,lon,List.of());}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<PontoRoteiroExcursaoDTO> planejamento(Long id,String uid,String sentido,boolean otimizar){
        ExcursaoDTO e=excursoes.consultar(id,uid); // Valida perfil e dono no serviço, inclusive chamadas internas.
        if(!"IDA".equals(sentido)&&!"VOLTA".equals(sentido))throw new IllegalArgumentException("Informe IDA ou VOLTA.");
        boolean volta="VOLTA".equals(sentido);
        if(volta&&(e.retornoEm()==null||e.origemLatitude()==null))throw new IllegalArgumentException("Retorno exige horário de volta e origem operacional configurados.");
        if(e.estado()!=EstadoExcursao.PUBLICADA||!(volta?e.retornoEm():e.partidaEm()).isAfter(clock.instant()))throw conflito();
        var confirmadas=inscricoes.findByExcursaoIdAndEstadoOrderByIdAsc(id,StatusInscricaoExcursao.CONFIRMADA);
        var coletas=new ArrayList<PontoRoteiroExcursaoDTO>();
        if(e.modoColeta()==ModoColetaExcursao.PORTA_A_PORTA){
            for(var i:confirmadas)coletas.add(new PontoRoteiroExcursaoDTO("COLETA",null,i.getNomePonto(),i.getLatitude(),i.getLongitude(),List.of(new PontoRoteiroExcursaoDTO.Participante(i.getId(),i.getNome()))));
        }else{
            var porParada=new HashMap<Long,List<PontoRoteiroExcursaoDTO.Participante>>();
            for(var i:confirmadas){
                if(i.getParada()==null||e.paradas().stream().noneMatch(p->p.id().equals(i.getParada().getId())))throw conflito();
                porParada.computeIfAbsent(i.getParada().getId(),v->new ArrayList<>()).add(new PontoRoteiroExcursaoDTO.Participante(i.getId(),i.getNome()));
            }
            for(var p:e.paradas())if(porParada.containsKey(p.id()))coletas.add(new PontoRoteiroExcursaoDTO("COLETA",p.id(),p.nome(),p.latitude(),p.longitude(),List.copyOf(porParada.get(p.id()))));
        }
        if(volta){Collections.reverse(coletas);coletas.replaceAll(p->new PontoRoteiroExcursaoDTO("DESEMBARQUE",p.paradaId(),p.nome(),p.latitude(),p.longitude(),p.participantes()));}
        var pontos=new ArrayList<PontoRoteiroExcursaoDTO>();
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
