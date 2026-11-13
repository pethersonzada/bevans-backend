package com.bevans.service;

import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.*;
import com.bevans.exception.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
public class RotaPublicaService {
    private final RotaPublicaRepository rotas;
    private final ParadaPublicaRepository paradas;
    private final TurmaRepository turmas;
    private final AlunoTurmaRepository vinculos;
    private final UsuarioRepository usuarios;
    private final ViagemRepository viagens;
    private final TurmaService turmaService;
    private final EntityManager em;
    private final int limiteParadas;
    private final EmbarqueRepository embarques;
    public RotaPublicaService(RotaPublicaRepository rotas,ParadaPublicaRepository paradas,TurmaRepository turmas,
            AlunoTurmaRepository vinculos,UsuarioRepository usuarios,ViagemRepository viagens,TurmaService turmaService,
            EntityManager em,@Value("${app.rotas-publicas.limite-paradas:50}") int limiteParadas,EmbarqueRepository embarques) {
        this.rotas=rotas;this.paradas=paradas;this.turmas=turmas;this.vinculos=vinculos;this.usuarios=usuarios;
        this.viagens=viagens;this.turmaService=turmaService;this.em=em;this.limiteParadas=limiteParadas;
        this.embarques=embarques;
    }
    private RuntimeException naoEncontrada() {return new ResponseStatusException(HttpStatus.NOT_FOUND,"Rota não encontrada.");}
    private Turma lockTurma(Long id) {
        Turma t=turmas.lockById(id).orElseThrow(this::naoEncontrada);
        em.refresh(t,LockModeType.PESSIMISTIC_WRITE);return t;
    }
    private void dono(Turma t,Long motoristaId) {
        if(t.getMotorista()==null || !Objects.equals(t.getMotorista().getId(),motoristaId)) throw new AccessDeniedException("Rota de outro motorista.");
    }
    private void antesDaPartida(Long turmaId) {
        if(viagens.findFirstByTurmaIdAndStatus(turmaId,"EM_ANDAMENTO").isPresent())
            throw new SolicitacaoConflictException("A rota não pode ser alterada durante viagem ativa.");
    }
    private RotaPublica lockRota(Long id,Long motoristaId) {
        RotaPublica r=rotas.findById(id).orElseThrow(this::naoEncontrada);
        Turma t=lockTurma(r.getTurma().getId());dono(t,motoristaId);
        em.refresh(r);antesDaPartida(t.getId());return r;
    }
    private String texto(String value,String campo,int limite) {
        if(value==null || value.isBlank() || value.trim().length()>limite) throw new IllegalArgumentException("Informe "+campo+" válido (até "+limite+" caracteres).");
        return value.trim();
    }
    private void coordenadas(Double lat,Double lon) {
        if(lat==null || lon==null || !Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180 || (lat==0 && lon==0))
            throw new IllegalArgumentException("Confirme coordenadas válidas no mapa.");
    }
    private void aplicar(RotaPublica r,RotaPublicaRequest d) {
        if(d==null || d.modo()==null || d.categoria()==null) throw new IllegalArgumentException("Informe modalidade e categoria.");
        coordenadas(d.origemLatitude(),d.origemLongitude());coordenadas(d.destinoLatitude(),d.destinoLongitude());
        if(d.descricao()!=null && d.descricao().length()>2000) throw new IllegalArgumentException("Descrição muito longa.");
        if(d.dias()==null || d.dias().isEmpty() || d.dias().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Informe os dias de operação.");
        if(d.horarioIda()==null && d.horarioVolta()==null) throw new IllegalArgumentException("Informe ao menos um horário.");
        r.setNome(texto(d.nome(),"nome",120));r.setCidade(texto(d.cidade(),"cidade de operação",120));
        r.setCategoria(d.categoria());r.setModo(d.modo());r.setOrigemNome(texto(d.origemNome(),"origem pública",255));
        r.setOrigemLatitude(d.origemLatitude());r.setOrigemLongitude(d.origemLongitude());
        r.setDestinoNome(texto(d.destinoNome(),"destino",255));r.setDestinoLatitude(d.destinoLatitude());r.setDestinoLongitude(d.destinoLongitude());
        r.setDescricao(d.descricao());r.setRegiao(d.regiao()==null?null:texto(d.regiao(),"região",255));
        r.setCampus(d.campus()==null?null:texto(d.campus(),"campus",255));r.getDias().clear();r.getDias().addAll(d.dias());
        r.setHorarioIda(d.horarioIda());r.setHorarioVolta(d.horarioVolta());
    }
    @Transactional
    public RotaPublicaDTO criar(Long motoristaId,RotaPublicaRequest dados) {
        if(dados==null || dados.turmaId()==null) throw new IllegalArgumentException("Informe a turma responsável pelas vagas.");
        Turma t=lockTurma(dados.turmaId());dono(t,motoristaId);antesDaPartida(t.getId());
        if(rotas.findByTurmaId(t.getId()).isPresent()) throw new SolicitacaoConflictException("Esta turma já tem uma publicação de rota.");
        RotaPublica r=new RotaPublica();r.setTurma(t);aplicar(r,dados);r.setPublicada(false);r.setAtiva(true);
        return dto(rotas.saveAndFlush(r));
    }
    @Transactional
    public RotaPublicaDTO atualizar(Long id,Long motoristaId,RotaPublicaRequest dados) {
        RotaPublica r=lockRota(id,motoristaId);
        if(dados==null || !Objects.equals(dados.turmaId(),r.getTurma().getId())) throw new IllegalArgumentException("Não é permitido trocar a turma da rota.");
        if(!embarques.findByRotaIdAndStatusAndDataOperacaoGreaterThanEqual(id,StatusEmbarque.APROVADO,java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"))).isEmpty()
                && (dados.modo()!=r.getModo()||!Objects.equals(dados.dias(),r.getDias())||!Objects.equals(dados.horarioIda(),r.getHorarioIda())||!Objects.equals(dados.horarioVolta(),r.getHorarioVolta())))
            throw new SolicitacaoConflictException("Há embarques aprovados: preserve modalidade, dias e horários.");
        aplicar(r,dados);return dto(rotas.saveAndFlush(r));
    }
    @Transactional
    public RotaPublicaDTO permitirPontoExcepcional(Long id,Long motoristaId,boolean permitir) {
        RotaPublica r=lockRota(id,motoristaId);
        r.setPermitePontoExcepcional(permitir);return dto(rotas.saveAndFlush(r));
    }
    @Transactional
    public RotaPublicaDTO publicar(Long id,Long motoristaId,boolean publicada) {
        RotaPublica r=lockRota(id,motoristaId);
        if(publicada && (turmas.capacidadeAtual(r.getTurma().getId())==null || !r.getAtiva()))
            throw new CapacidadeException("Configure a capacidade e ative a rota antes de publicar.");
        r.setPublicada(publicada);return dto(rotas.saveAndFlush(r));
    }
    @Transactional
    public RotaPublicaDTO disponibilidade(Long id,Long motoristaId,boolean ativa) {
        RotaPublica r=lockRota(id,motoristaId);r.setAtiva(ativa);if(!ativa)r.setPublicada(false);
        return dto(rotas.saveAndFlush(r));
    }
    @Transactional
    public RotaPublicaDTO salvarParadas(Long id,Long motoristaId,List<ParadaPublicaDTO> dados) {
        RotaPublica r=lockRota(id,motoristaId);
        if(dados==null || dados.size()>limiteParadas) throw new IllegalArgumentException("Limite de "+limiteParadas+" paradas por rota.");
        List<ParadaPublica> existentes=paradas.findByRotaIdOrderByOrdemAscIdAsc(id);
        Map<Long,ParadaPublica> porId=new HashMap<>();existentes.forEach(p->porId.put(p.getId(),p));
        Set<Long> ids=new HashSet<>();
        for(ParadaPublicaDTO p:dados) {
            if(p==null)throw new IllegalArgumentException("Parada inválida.");texto(p.nome(),"nome da parada",120);coordenadas(p.latitude(),p.longitude());
            if(p.id()!=null && (!porId.containsKey(p.id()) || !ids.add(p.id()))) throw new IllegalArgumentException("Parada duplicada ou pertencente a outra rota.");
        }
        var confirmados=embarques.findByRotaIdAndStatusAndDataOperacaoGreaterThanEqual(id,StatusEmbarque.APROVADO,java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")));
        for(SolicitacaoEmbarque e:confirmados) {
            if(e.getParada()==null)continue;
            ParadaPublica atual=porId.get(e.getParada().getId());
            ParadaPublicaDTO nova=dados.stream().filter(p->Objects.equals(p.id(),e.getParada().getId())).findFirst().orElse(null);
            if(atual==null||nova==null||nova.latitude()!=atual.getLatitude()||nova.longitude()!=atual.getLongitude()||!Objects.equals(nova.horario(),atual.getHorario()))
                throw new SolicitacaoConflictException("Uma parada com embarque aprovado não pode ser removida ou reposicionada.");
        }
        // Exclusão lógica preserva IDs usados por futuras solicitações/viagens.
        existentes.forEach(p->p.setAtiva(false));
        int ordem=0;
        for(ParadaPublicaDTO d:dados) {
            ParadaPublica p=d.id()==null?new ParadaPublica():porId.get(d.id());p.setRota(r);p.setAtiva(true);p.setOrdem(ordem++);
            p.setNome(d.nome().trim());p.setLatitude(d.latitude());p.setLongitude(d.longitude());p.setHorario(d.horario());paradas.save(p);
        }
        paradas.flush();return dto(r);
    }
    @Transactional(readOnly=true)
    public RotaPublicaDTO consultar(Long id) {return dto(rotas.findById(id).orElseThrow(this::naoEncontrada));}
    @Transactional(readOnly=true)
    public List<RotaPublicaDTO> minhas(Long motoristaId) {return rotas.findByTurmaMotoristaId(motoristaId).stream().map(this::dto).toList();}
    @Transactional
    public List<PontoPublicoDTO> planejamento(Long id,Long motoristaId,String sentido,boolean otimizar) {
        if(!"IDA".equals(sentido) && !"VOLTA".equals(sentido))throw new IllegalArgumentException("Informe IDA ou VOLTA.");
        RotaPublica r=lockRota(id,motoristaId);
        List<PontoPublicoDTO> pontos=new ArrayList<>();
        pontos.add(new PontoPublicoDTO(null,r.getOrigemNome(),r.getOrigemLatitude(),r.getOrigemLongitude()));
        for(ParadaPublicaDTO p:dto(r).paradas())pontos.add(new PontoPublicoDTO(p.id(),p.nome(),p.latitude(),p.longitude()));
        pontos.add(new PontoPublicoDTO(null,r.getDestinoNome(),r.getDestinoLatitude(),r.getDestinoLongitude()));
        if("VOLTA".equals(sentido))Collections.reverse(pontos);
        // FIXA nunca troca a sequência cadastrada. Prévia não altera registros.
        return otimizar && r.getModo()==ModoRota.FLEXIVEL?OtimizadorParadas.ordenar(pontos):pontos;
    }
    @Transactional
    public SolicitacaoVagaDTO solicitar(Long rotaId,Long alunoId) {
        RotaPublica r=rotas.findById(rotaId).orElseThrow(this::naoEncontrada);
        Turma t=lockTurma(r.getTurma().getId());em.refresh(r);
        if(!r.getPublicada() || !r.getAtiva())throw naoEncontrada();antesDaPartida(t.getId());
        Usuario aluno=usuarios.findById(alunoId).orElseThrow(()->new AccessDeniedException("Sessão inválida."));
        if(!"PASSAGEIRO".equals(aluno.getTipo()))throw new AccessDeniedException("Somente passageiros solicitam vagas.");
        AlunoTurma v=vinculos.findByTurmaIdAndAlunoId(t.getId(),alunoId).orElse(null);
        if(v!=null && ("PENDENTE".equals(v.getStatus())||"APROVADO".equals(v.getStatus())))
            throw new SolicitacaoConflictException("Você já possui um vínculo aprovado ou pendente nesta rota.");
        Integer cap=turmas.capacidadeAtual(t.getId());
        if(cap==null || vinculos.countByTurmaIdAndStatus(t.getId(),"APROVADO")>=cap)throw new CapacidadeException("Rota sem vagas disponíveis.");
        if(v==null){v=new AlunoTurma();v.setTurma(t);v.setAluno(aluno);}v.setStatus("PENDENTE");v=vinculos.saveAndFlush(v);
        return new SolicitacaoVagaDTO(v.getId(),t.getId(),r.getNome(),v.getStatus());
    }
    private RotaPublicaDTO dto(RotaPublica r) {
        List<ParadaPublicaDTO> pontos=paradas.findByRotaIdOrderByOrdemAscIdAsc(r.getId()).stream().filter(ParadaPublica::getAtiva)
                .map(p->new ParadaPublicaDTO(p.getId(),p.getNome(),p.getLatitude(),p.getLongitude(),p.getHorario())).toList();
        return new RotaPublicaDTO(r.getId(),r.getTurma().getId(),r.getNome(),r.getCidade(),r.getCategoria(),r.getModo(),r.getOrigemNome(),
                r.getOrigemLatitude(),r.getOrigemLongitude(),r.getDestinoNome(),r.getDestinoLatitude(),r.getDestinoLongitude(),r.getDescricao(),
                r.getRegiao(),r.getCampus(),Set.copyOf(r.getDias()),r.getHorarioIda(),r.getHorarioVolta(),r.getPublicada(),r.getAtiva(),
                turmaService.consultarVagas(r.getTurma().getId()),pontos,false,r.getPermitePontoExcepcional());
    }
}
