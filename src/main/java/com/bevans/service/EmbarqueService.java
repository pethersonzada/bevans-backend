package com.bevans.service;

import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.*;
import com.bevans.exception.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class EmbarqueService {
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private org.springframework.context.ApplicationEventPublisher eventos;
    private final EmbarqueRepository pedidos;
    private final RotaPublicaRepository rotas;
    private final TurmaRepository turmas;
    private final AlunoTurmaRepository vinculos;
    private final UsuarioRepository usuarios;
    private final ParadaPublicaRepository paradas;
    private final ViagemRepository viagens;
    private final TurmaService turmaService;
    private final RotaPublicaService rotaService;
    private final EntityManager em;
    private final Clock clock;
    private final int antecedencia;

    public EmbarqueService(EmbarqueRepository pedidos,RotaPublicaRepository rotas,TurmaRepository turmas,
            AlunoTurmaRepository vinculos,UsuarioRepository usuarios,ParadaPublicaRepository paradas,ViagemRepository viagens,
            TurmaService turmaService,RotaPublicaService rotaService,EntityManager em,Clock clock,
            @Value("${app.rotas-publicas.antecedencia-dias:30}") int antecedencia) {
        this.pedidos=pedidos;this.rotas=rotas;this.turmas=turmas;this.vinculos=vinculos;this.usuarios=usuarios;
        this.paradas=paradas;this.viagens=viagens;this.turmaService=turmaService;this.rotaService=rotaService;
        this.em=em;this.clock=clock;this.antecedencia=antecedencia;
    }
    private RuntimeException ausente() {return new ResponseStatusException(HttpStatus.NOT_FOUND,"Embarque ou rota não encontrado.");}
    private RotaPublica lockRota(Long id) {
        RotaPublica r=rotas.findById(id).orElseThrow(this::ausente);
        if(r.getTurma()==null || r.getTurma().isExcluido())throw ausente();
        Turma t=turmas.lockById(r.getTurma().getId()).orElseThrow(this::ausente);
        em.refresh(t,LockModeType.PESSIMISTIC_WRITE);em.refresh(r);
        return r;
    }
    private void dono(RotaPublica r,Long motorista) {
        if(r.getTurma().getMotorista()==null || !Objects.equals(r.getTurma().getMotorista().getId(),motorista))throw new AccessDeniedException("Rota de outro motorista.");
    }
    private void antesDaPartida(RotaPublica r,LocalDate data,String sentido) {
        if(!"IDA".equals(sentido)&&!"VOLTA".equals(sentido))throw new IllegalArgumentException("Informe sentido IDA ou VOLTA.");
        LocalDate hoje=LocalDate.now(clock);
        if(data==null || data.isBefore(hoje) || data.isAfter(hoje.plusDays(antecedencia)))throw new IllegalArgumentException("Data fora da janela permitida de embarque.");
        LocalTime hora="IDA".equals(sentido)?r.getHorarioIda():r.getHorarioVolta();
        if(!r.getDias().contains(data.getDayOfWeek()) || hora==null)throw new SolicitacaoConflictException("A rota não funciona nesse dia e sentido.");
        if(!LocalDateTime.now(clock).isBefore(data.atTime(hora)))throw new SolicitacaoConflictException("O horário de partida já passou.");
        if(viagens.findFirstByTurmaIdAndStatus(r.getTurma().getId(),"EM_ANDAMENTO").isPresent())throw new SolicitacaoConflictException("Não é permitido alterar embarques durante viagem ativa.");
    }
    private ParadaPublica parada(RotaPublica r,Long id) {
        ParadaPublica p=paradas.findById(id).orElseThrow(this::ausente);
        em.refresh(p);
        if(!p.getAtiva() || !Objects.equals(p.getRota().getId(),r.getId()))throw new IllegalArgumentException("Selecione uma parada ativa desta rota.");
        return p;
    }
    private void coordenadas(Double lat,Double lon) {
        if(lat==null||lon==null||!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180||(lat==0&&lon==0))
            throw new IllegalArgumentException("Confirme um ponto válido no mapa.");
    }
    private Usuario passageiro(Long id) {
        Usuario u=usuarios.findById(id).orElseThrow(()->new AccessDeniedException("Sessão inválida."));
        if(!"PASSAGEIRO".equals(u.getTipo()))throw new AccessDeniedException("Somente passageiros solicitam embarque.");return u;
    }
    @Transactional
    public EmbarqueDTO solicitar(Long rotaId,Long alunoId,EmbarqueRequest d) {
        if(d==null)throw new IllegalArgumentException("Informe o embarque.");
        RotaPublica r=lockRota(rotaId);
        if(!r.getPublicada()||!r.getAtiva())throw ausente();
        antesDaPartida(r,d.dataOperacao(),d.sentido());Usuario aluno=passageiro(alunoId);
        SolicitacaoEmbarque e=new SolicitacaoEmbarque();e.setRota(r);e.setAluno(aluno);e.setDataOperacao(d.dataOperacao());e.setSentido(d.sentido());
        if(d.observacao()!=null&&d.observacao().length()>500)throw new IllegalArgumentException("Observação deve ter até 500 caracteres.");
        e.setObservacao(d.observacao()==null?null:d.observacao().trim());
        if(d.paradaId()!=null) {
            if(d.latitude()!=null||d.longitude()!=null||d.nomePonto()!=null||Boolean.TRUE.equals(d.pontoConfirmado()))throw new IllegalArgumentException("Envie somente paradaId ao escolher uma parada cadastrada.");
            ParadaPublica p=parada(r,d.paradaId());e.setParada(p);e.setNomePonto(p.getNome());e.setLatitude(p.getLatitude());e.setLongitude(p.getLongitude());
        } else {
            if(r.getModo()!=ModoRota.FLEXIVEL&&!r.getPermitePontoExcepcional())throw new IllegalArgumentException("Rota FIXA exige uma parada cadastrada ou permissão explícita de exceção.");
            if(!Boolean.TRUE.equals(d.pontoConfirmado())||d.nomePonto()==null||d.nomePonto().isBlank()||d.nomePonto().trim().length()>120)
                throw new IllegalArgumentException("Confirme o pin e o nome do ponto de embarque.");
            coordenadas(d.latitude(),d.longitude());e.setNomePonto(d.nomePonto().trim());e.setLatitude(d.latitude());e.setLongitude(d.longitude());
        }
        var abertos=pedidos.abertos(rotaId,alunoId,d.dataOperacao(),d.sentido(),List.of(StatusEmbarque.PENDENTE,StatusEmbarque.APROVADO));
        if(!abertos.isEmpty()) {
            SolicitacaoEmbarque atual=abertos.get(0);
            if(Objects.equals(atual.getParada()==null?null:atual.getParada().getId(),d.paradaId())&&Objects.equals(atual.getNomePonto(),e.getNomePonto())
                    &&atual.getLatitude()==e.getLatitude()&&atual.getLongitude()==e.getLongitude()&&Objects.equals(atual.getObservacao(),e.getObservacao()))return dto(atual);
            throw new SolicitacaoConflictException("Já existe um pedido aberto para esta data e sentido. Cancele-o antes de alterar o ponto.");
        }
        var vinculo=vinculos.findByTurmaIdAndAlunoId(r.getTurma().getId(),alunoId).orElse(null);
        boolean possuiVaga=vinculo!=null&&"APROVADO".equals(vinculos.statusAtual(vinculo.getId()));
        Integer cap=turmas.capacidadeAtual(r.getTurma().getId());
        if(!possuiVaga&&(cap==null||vinculos.countByTurmaIdAndStatus(r.getTurma().getId(),"APROVADO")>=cap))throw new CapacidadeException("Rota sem vagas disponíveis.");
        e.setStatus(StatusEmbarque.PENDENTE);e.setCriadoEm(clock.instant());return dto(pedidos.saveAndFlush(e));
    }
    @Transactional
    public EmbarqueDTO analisar(Long id,Long motorista,StatusEmbarque status) {
        if(status!=StatusEmbarque.APROVADO&&status!=StatusEmbarque.REJEITADO)throw new IllegalArgumentException("Informe APROVADO ou REJEITADO.");
        SolicitacaoEmbarque e=pedidos.findById(id).orElseThrow(this::ausente);
        RotaPublica r=lockRota(e.getRota().getId());dono(r,motorista);em.refresh(e);
        antesDaPartida(r,e.getDataOperacao(),e.getSentido());
        if(e.getStatus()==status)return dto(e);
        if(e.getStatus()!=StatusEmbarque.PENDENTE)throw new SolicitacaoConflictException("Pedido já foi analisado ou cancelado.");
        if(status==StatusEmbarque.APROVADO) {
            if(!r.getAtiva()||!r.getPublicada())throw new SolicitacaoConflictException("Rota indisponível para aprovação.");
            if(e.getParada()==null&&r.getModo()==ModoRota.FIXA&&!r.getPermitePontoExcepcional())
                throw new SolicitacaoConflictException("Motorista desabilitou pedidos excepcionais nesta rota FIXA.");
            if(e.getParada()!=null) {
                ParadaPublica p=parada(r,e.getParada().getId());
                e.setNomePonto(p.getNome());e.setLatitude(p.getLatitude());e.setLongitude(p.getLongitude());
            }
            AlunoTurma v=vinculos.findByTurmaIdAndAlunoId(r.getTurma().getId(),e.getAluno().getId()).orElse(null);
            if(v==null){v=new AlunoTurma();v.setTurma(r.getTurma());v.setAluno(e.getAluno());}
            if(v.getId()==null||!"APROVADO".equals(vinculos.statusAtual(v.getId()))) {
                v.setStatus("PENDENTE");v=vinculos.saveAndFlush(v);
                turmaService.analisarSolicitacao(v.getId(),"APROVADO");
            }
            e.setVinculoId(v.getId());
        }
        e.setStatus(status);e.setAnalisadoEm(clock.instant());e.setAnalisadoPor(motorista);pedidos.saveAndFlush(e);
        if(eventos!=null)eventos.publishEvent(new EventosDeViagem.Direto(status==StatusEmbarque.APROVADO?"SOLICITACAO_APROVADA":"SOLICITACAO_RECUSADA","EMBARQUE",e.getId(),e.getAluno().getId(),null,"PEDIDO:"+e.getId()+":"+status));
        return dto(e);
    }
    @Transactional
    public EmbarqueDTO cancelar(Long id,Long alunoId) {
        SolicitacaoEmbarque e=pedidos.findById(id).orElseThrow(this::ausente);RotaPublica r=lockRota(e.getRota().getId());em.refresh(e);
        if(!Objects.equals(e.getAluno().getId(),alunoId))throw new AccessDeniedException("Pedido de outro passageiro.");
        if(e.getStatus()==StatusEmbarque.CANCELADO)return dto(e);
        antesDaPartida(r,e.getDataOperacao(),e.getSentido());
        if(e.getStatus()!=StatusEmbarque.PENDENTE)throw new SolicitacaoConflictException("Somente pedidos pendentes podem ser cancelados.");
        e.setStatus(StatusEmbarque.CANCELADO);return dto(pedidos.saveAndFlush(e));
    }
    @Transactional(readOnly=true)
    public List<EmbarqueDTO> minhas(Long alunoId) {return pedidos.findByAlunoIdOrderByIdDesc(alunoId).stream().map(this::dto).toList();}
    @Transactional(readOnly=true)
    public List<EmbarqueDTO> daRota(Long rotaId,Long motorista,LocalDate data,String sentido) {
        RotaPublica r=rotas.findById(rotaId).orElseThrow(this::ausente);dono(r,motorista);
        if(data==null||(!"IDA".equals(sentido)&&!"VOLTA".equals(sentido)))throw new IllegalArgumentException("Informe data e sentido.");
        return pedidos.findByRotaIdAndDataOperacaoAndSentidoOrderByIdAsc(rotaId,data,sentido).stream().map(this::dto).toList();
    }
    @Transactional
    public List<PontoPublicoDTO> planejamento(Long rotaId,Long motorista,LocalDate data,String sentido,boolean otimizar) {
        RotaPublica r=lockRota(rotaId);dono(r,motorista);antesDaPartida(r,data,sentido);
        List<PontoPublicoDTO> pontos=new ArrayList<>(rotaService.planejamento(rotaId,motorista,sentido,false));
        PontoPublicoDTO fim=pontos.remove(pontos.size()-1);
        for(SolicitacaoEmbarque e:pedidos.findByRotaIdAndDataOperacaoAndSentidoOrderByIdAsc(rotaId,data,sentido)) {
            if(e.getStatus()!=StatusEmbarque.APROVADO||e.getVinculoId()==null||!"APROVADO".equals(vinculos.statusAtual(e.getVinculoId())))continue;
            if(e.getParada()==null) pontos.add(new PontoPublicoDTO(null,e.getNomePonto(),e.getLatitude(),e.getLongitude()));
        }
        pontos.add(fim);
        return otimizar&&r.getModo()==ModoRota.FLEXIVEL?OtimizadorParadas.ordenar(pontos):pontos;
    }
    private EmbarqueDTO dto(SolicitacaoEmbarque e) {
        return new EmbarqueDTO(e.getId(),e.getRota().getId(),e.getAluno().getId(),e.getDataOperacao(),e.getSentido(),e.getParada()==null?null:e.getParada().getId(),
                e.getNomePonto(),e.getLatitude(),e.getLongitude(),e.getObservacao(),e.getStatus(),e.getCriadoEm(),e.getAnalisadoEm());
    }
}
