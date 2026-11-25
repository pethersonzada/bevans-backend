package com.bevans.service;
import com.bevans.model.*;
import com.bevans.dto.*;
import com.bevans.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.context.*;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@Service
public class OperacaoService {
    private final OperacaoViagemRepository operacoes;private final RegistroOperacionalRepository registros;private final EventoOperacionalRepository eventos;
    private final ViagemRepository viagens;private final ExecucaoExcursaoRepository execucoes;private final TurmaRepository turmas;
    private final ExcursaoRepository excursoes;private final UsuarioRepository usuarios;private final AlunoTurmaRepository vinculos;
    private final RotaPublicaRepository publicas;private final ParadaPublicaRepository paradas;private final EmbarqueRepository embarques;
    private final PresencaRepository presencas;private final InscricaoExcursaoRepository inscricoes;private final CredencialInscricaoExcursaoRepository credenciais;
    private final ColetaPrivadaService coletas;private final EntityManager em;private final Clock clock;private final ApplicationEventPublisher publisher;
    public OperacaoService(OperacaoViagemRepository o,RegistroOperacionalRepository r,EventoOperacionalRepository e,ViagemRepository v,
            ExecucaoExcursaoRepository x,TurmaRepository t,ExcursaoRepository ex,UsuarioRepository u,AlunoTurmaRepository a,
            RotaPublicaRepository p,ParadaPublicaRepository pp,EmbarqueRepository b,PresencaRepository pr,InscricaoExcursaoRepository i,
            CredencialInscricaoExcursaoRepository c,ColetaPrivadaService cp,EntityManager em,Clock clock,ApplicationEventPublisher publisher){
        operacoes=o;registros=r;eventos=e;viagens=v;execucoes=x;turmas=t;excursoes=ex;usuarios=u;vinculos=a;publicas=p;paradas=pp;embarques=b;presencas=pr;inscricoes=i;credenciais=c;coletas=cp;this.em=em;this.clock=clock;this.publisher=publisher;
    }
    private ResponseStatusException naoEncontrado(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Operação ou passageiro não encontrado.");}
    private ResponseStatusException conflito(String s){return new ResponseStatusException(HttpStatus.CONFLICT,s);}
    private Usuario usuario(String uid){return usuarios.findByFirebaseUid(uid).orElseThrow(()->new AccessDeniedException("Perfil inválido."));}
    private OperacaoViagem buscar(TipoOperacao tipo,Long ref){return operacoes.findByTipoAndReferenciaId(tipo,ref).orElseThrow(this::naoEncontrado);}
    private void dono(OperacaoViagem o,String uid){var u=usuario(uid);if(!"MOTORISTA".equals(u.getTipo())||!o.motorista.getId().equals(u.getId()))throw new AccessDeniedException("Operação de outro motorista.");}
    private void lock(OperacaoViagem o){
        if(o.tipo==TipoOperacao.VIAGEM){var t=turmas.lockById(o.viagem.getTurma().getId()).orElseThrow(this::naoEncontrado);em.refresh(t,LockModeType.PESSIMISTIC_WRITE);em.refresh(o.viagem);}
        else{var e=excursoes.lockById(o.execucao.getExcursao().getId()).orElseThrow(this::naoEncontrado);em.refresh(e,LockModeType.PESSIMISTIC_WRITE);em.refresh(o.execucao);}
    }
    private boolean ativa(OperacaoViagem o){return o.tipo==TipoOperacao.VIAGEM?"EM_ANDAMENTO".equals(o.viagem.getStatus()):o.execucao.getEstado()==EstadoExecucaoExcursao.EM_ANDAMENTO;}
    @EventListener @Transactional public void viagem(EventosDeViagem.Viagem e){
        if("INICIADA".equals(e.tipo()))prepararViagem(viagens.findById(e.id()).orElseThrow(this::naoEncontrado));
        else ciclo(TipoOperacao.VIAGEM,e.id(),e.tipo());
    }
    @EventListener @Transactional public void excursao(EventosDeViagem.Excursao e){
        if("INICIADA".equals(e.tipo()))prepararExcursao(execucoes.findById(e.id()).orElseThrow(this::naoEncontrado));
        else ciclo(TipoOperacao.EXCURSAO,e.id(),e.tipo());
    }
    private void ciclo(TipoOperacao tipo,Long id,String evento){
        var o=operacoes.findByTipoAndReferenciaId(tipo,id).orElse(null);if(o==null)return;
        if("VALIDAR_FIM".equals(evento)){if(registros.countByOperacaoIdAndEstado(o.id,EstadoOperacional.EMBARCADO)>0)throw conflito("Há passageiros na van: confirme desembarques antes de encerrar.");return;}
        publisher.publishEvent(new EventosDeViagem.Operacao(o.id,null,"VIAGEM_"+evento,"CICLO:"+o.id+":"+evento));
    }
    private OperacaoViagem nova(TipoOperacao tipo,Long ref,Usuario motorista,String sentido){var o=new OperacaoViagem();o.tipo=tipo;o.referenciaId=ref;o.motorista=motorista;o.sentido=sentido;return o;}
    private void adicionar(OperacaoViagem o,Long aluno,InscricaoExcursao inscricao,String nome,Long parada,int ordem,double lat,double lon,Set<Long> vistos){
        Long identidade=aluno==null?inscricao.getId():aluno;if(!vistos.add(identidade))return;
        var r=new RegistroOperacional();r.operacao=o;r.aluno=aluno==null?null:em.getReference(Usuario.class,aluno);r.inscricao=inscricao;r.nome=nome;r.paradaId=parada;r.ordem=ordem;r.latitude=lat;r.longitude=lon;registros.save(r);
    }
    private void prepararViagem(Viagem v){
        if(operacoes.findByTipoAndReferenciaId(TipoOperacao.VIAGEM,v.getId()).isPresent())return;
        var o=nova(TipoOperacao.VIAGEM,v.getId(),v.getTurma().getMotorista(),v.getSentido());o.viagem=v;var vistos=new HashSet<Long>();
        LocalDate data=v.getIniciadaEm().atZone(clock.getZone()).toLocalDate();
        if(!"PRIVADA".equals(v.getModalidade())&&!"PUBLICA".equals(v.getModalidade()))
            throw conflito("Viagem antiga sem modalidade explícita; não é seguro reconstruir roteiro.");
        var publica="PUBLICA".equals(v.getModalidade())
            ?publicas.findById(v.getRotaPublicaId()).orElseThrow(()->conflito("Rota pública desta viagem não encontrada."))
            :null;
        if(publica!=null&&!java.util.Objects.equals(publica.getTurma().getId(),v.getTurma().getId()))
            throw conflito("Rota pública de outra turma.");
        List<PontoColetaPrivadaDTO> rota;
        if(publica!=null){
            rota=roteiroPublico(publica,data,v.getSentido());
        }else{
            rota=coletas.roteiroSistema(v.getTurma().getId(),v.getSentido(),false);
            var esperados=presencas.findByDataAndTurmaId(data,v.getTurma().getId()).stream().filter(p->PresencasEsperadas.inclui(p.getStatus(),v.getSentido())).map(p->p.getUsuario().getId()).collect(java.util.stream.Collectors.toSet());
            var presentes=rota.stream().flatMap(p->p.passageiros().stream()).map(PassageiroRotaDTO::getId).collect(java.util.stream.Collectors.toSet());
            if(!presentes.equals(esperados))throw conflito("Há passageiros sem ponto confirmado; revise o planejamento.");
        }
        try{o.salvarRoteiro(new ObjectMapper().writeValueAsString(rota));}catch(Exception ex){throw conflito("Não foi possível salvar o roteiro operacional.");}
        operacoes.saveAndFlush(o);int n=0;
        for(var p:rota){for(var a:p.passageiros())adicionar(o,a.getId(),null,a.getNome(),p.paradaId(),n,p.latitude(),p.longitude(),vistos);n++;}
        Integer capacidade=turmas.capacidadeAtual(v.getTurma().getId());
        if(capacidade!=null&&vistos.size()>capacidade)throw conflito("Passageiros confirmados excedem a capacidade da viagem.");
        registros.flush();publisher.publishEvent(new EventosDeViagem.Operacao(o.id,null,"VIAGEM_INICIADA","CICLO:"+o.id+":INICIADA"));
    }

    private void pontoPublicoValido(double lat,double lon){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180||(lat==0&&lon==0))throw conflito("Rota pública possui ponto sem coordenadas válidas.");
    }
    private List<PontoColetaPrivadaDTO> roteiroPublico(RotaPublica r,LocalDate data,String sentido){
        boolean volta="VOLTA".equals(sentido);
        if(!r.getAtiva()||!r.getPublicada())throw conflito("Publique e ative a rota pública antes de iniciar.");
        if(!r.getDias().contains(data.getDayOfWeek())||(volta?r.getHorarioVolta():r.getHorarioIda())==null)throw conflito("A rota pública não funciona neste dia e sentido.");
        var ps=new ArrayList<>(paradas.findByRotaIdOrderByOrdemAscIdAsc(r.getId()).stream().filter(ParadaPublica::getAtiva).toList());
        if(volta)Collections.reverse(ps);
        var ids=ps.stream().map(ParadaPublica::getId).collect(java.util.stream.Collectors.toSet());
        var membros=new HashMap<Long,List<PassageiroRotaDTO>>();var excepcionais=new ArrayList<PontoColetaPrivadaDTO>();
        for(var e:embarques.findByRotaIdAndDataOperacaoAndSentidoOrderByIdAsc(r.getId(),data,sentido)){
            if(e.getStatus()!=StatusEmbarque.APROVADO||e.getVinculoId()==null||!"APROVADO".equals(vinculos.statusAtual(e.getVinculoId())))continue;
            var a=new PassageiroRotaDTO(e.getAluno().getId(),e.getAluno().getNome(),e.getLatitude(),e.getLongitude());
            if(e.getParada()!=null){
                if(!ids.contains(e.getParada().getId()))throw conflito("Parada aprovada não está ativa nesta rota.");
                membros.computeIfAbsent(e.getParada().getId(),k->new ArrayList<>()).add(a);
            }else{
                pontoPublicoValido(e.getLatitude(),e.getLongitude());
                excepcionais.add(new PontoColetaPrivadaDTO("EXCEPCIONAL",null,e.getNomePonto(),e.getLatitude(),e.getLongitude(),List.of(a)));
            }
        }
        var pontos=new ArrayList<PontoColetaPrivadaDTO>();
        pontos.add(new PontoColetaPrivadaDTO("ORIGEM",null,volta?r.getDestinoNome():r.getOrigemNome(),volta?r.getDestinoLatitude():r.getOrigemLatitude(),volta?r.getDestinoLongitude():r.getOrigemLongitude(),List.of()));
        for(var p:ps)pontos.add(new PontoColetaPrivadaDTO("PARADA",p.getId(),p.getNome(),p.getLatitude(),p.getLongitude(),List.copyOf(membros.getOrDefault(p.getId(),List.of()))));
        pontos.addAll(excepcionais);
        pontos.add(new PontoColetaPrivadaDTO("DESTINO",null,volta?r.getOrigemNome():r.getDestinoNome(),volta?r.getOrigemLatitude():r.getDestinoLatitude(),volta?r.getOrigemLongitude():r.getDestinoLongitude(),List.of()));
        pontos.forEach(p->pontoPublicoValido(p.latitude(),p.longitude()));
        // Mesma política da prévia otimizada: FIXA mantém a sequência obrigatória.
        if(r.getModo()==ModoRota.FIXA)return List.copyOf(pontos);
        var entrada=new ArrayList<PontoPublicoDTO>();for(int n=0;n<pontos.size();n++){var p=pontos.get(n);entrada.add(new PontoPublicoDTO((long)n,p.nome(),p.latitude(),p.longitude()));}
        return OtimizadorParadas.ordenar(entrada).stream().map(p->pontos.get(Math.toIntExact(p.paradaId()))).toList();
    }

    @Transactional(readOnly=true) public List<PontoColetaPrivadaDTO> roteiroViagem(Long ref,String uid){
        var o=buscar(TipoOperacao.VIAGEM,ref);dono(o,uid);
        if(o.getRoteiroJson()==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Esta operação antiga não possui roteiro completo salvo. Não é seguro reconstruí-lo com dados atuais.");
        try{
            List<PontoColetaPrivadaDTO> pontos=new ObjectMapper().readValue(o.getRoteiroJson(),new TypeReference<List<PontoColetaPrivadaDTO>>(){});
            if(pontos==null||pontos.size()<2)throw new IllegalArgumentException();return List.copyOf(pontos);
        }catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Roteiro operacional salvo indisponível.");}
    }
    private void prepararExcursao(ExecucaoExcursao x){
        if(operacoes.findByTipoAndReferenciaId(TipoOperacao.EXCURSAO,x.getId()).isPresent())return;
        var o=nova(TipoOperacao.EXCURSAO,x.getId(),x.getExcursao().getOrganizador(),x.getSentido());o.execucao=x;operacoes.saveAndFlush(o);var vistos=new HashSet<Long>();
        List<PontoRoteiroExcursaoDTO> pontos;
        try{pontos=new ObjectMapper().readValue(x.getRoteiroJson(),new TypeReference<List<PontoRoteiroExcursaoDTO>>(){});}catch(Exception ex){throw conflito("Snapshot indisponível.");}
        int n=0;for(var p:pontos){for(var a:p.participantes()){
            var i=inscricoes.findById(a.inscricaoId()).filter(v->v.getExcursao().getId().equals(x.getExcursao().getId())&&v.getEstado()==StatusInscricaoExcursao.CONFIRMADA).orElseThrow(()->conflito("Inscrição incompatível com a execução."));
            adicionar(o,null,i,a.nome(),p.paradaId(),n,p.latitude(),p.longitude(),vistos);
        }n++;}registros.flush();publisher.publishEvent(new EventosDeViagem.Operacao(o.id,null,"VIAGEM_INICIADA","CICLO:"+o.id+":INICIADA"));
    }
    @Transactional public void prepararLegada(TipoOperacao tipo,Long id,String uid){
        var u=usuario(uid);if(!"MOTORISTA".equals(u.getTipo()))throw new AccessDeniedException("Somente motorista.");
        if(tipo==TipoOperacao.VIAGEM){var v=viagens.findById(id).orElseThrow(this::naoEncontrado);if(!v.getTurma().getMotorista().getId().equals(u.getId()))throw new AccessDeniedException("Viagem alheia.");turmas.lockById(v.getTurma().getId()).orElseThrow(this::naoEncontrado);em.refresh(v);if(!"EM_ANDAMENTO".equals(v.getStatus()))throw conflito("Viagem encerrada.");prepararViagem(v);}
        else{var x=execucoes.findById(id).orElseThrow(this::naoEncontrado);if(!x.getExcursao().getOrganizador().getId().equals(u.getId()))throw new AccessDeniedException("Excursão alheia.");excursoes.lockById(x.getExcursao().getId()).orElseThrow(this::naoEncontrado);em.refresh(x);if(x.getEstado()!=EstadoExecucaoExcursao.EM_ANDAMENTO)throw conflito("Execução encerrada.");prepararExcursao(x);}
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public OperacaoDTO painel(TipoOperacao tipo,Long ref,String uid){
        var o=buscar(tipo,ref);dono(o,uid);var rs=registros.findByOperacaoIdOrderByOrdemAscIdAsc(o.id);long a=rs.stream().filter(r->r.estado==EstadoOperacional.AGUARDANDO).count(),b=rs.stream().filter(r->r.estado==EstadoOperacional.EMBARCADO).count(),n=rs.stream().filter(r->r.estado==EstadoOperacional.NAO_EMBARCOU).count(),d=rs.stream().filter(r->r.estado==EstadoOperacional.DESEMBARCADO).count();
        return new OperacaoDTO(o.id,o.tipo,o.referenciaId,o.sentido,new OperacaoDTO.Contadores(rs.size(),a,b,n,d),rs.stream().map(this::dto).toList());
    }
    private RegistroOperacional proprio(OperacaoViagem o,String uid){
        var u=usuario(uid);if(o.tipo!=TipoOperacao.VIAGEM||!"PASSAGEIRO".equals(u.getTipo())||!vinculos.existsByTurmaIdAndAlunoIdAndStatus(o.viagem.getTurma().getId(),u.getId(),"APROVADO"))throw new AccessDeniedException("Passageiro não autorizado.");
        return registros.findByOperacaoIdAndAlunoId(o.id,u.getId()).orElseThrow(this::naoEncontrado);
    }
    private RegistroOperacional web(OperacaoViagem o,String token){
        String hash;try{hash=TokensExcursao.hash(token);}catch(IllegalArgumentException ex){throw naoEncontrado();}
        var c=credenciais.findByTokenHash(hash).filter(v->v.getExpiraEm().isAfter(clock.instant())).orElseThrow(this::naoEncontrado);
        if(o.tipo!=TipoOperacao.EXCURSAO||!c.getExcursao().getId().equals(o.execucao.getExcursao().getId()))throw naoEncontrado();
        var i=inscricoes.findByCredencialId(c.getId()).orElseThrow(this::naoEncontrado);return registros.findByOperacaoIdAndInscricaoId(o.id,i.getId()).orElseThrow(this::naoEncontrado);
    }
    @Transactional(readOnly=true) public OperacaoDTO.Registro minha(Long ref,String uid){return dto(proprio(buscar(TipoOperacao.VIAGEM,ref),uid));}
    /**
     * Retorna somente a posição operacional do próprio passageiro.
     * Uma parada compartilhada conta uma vez, e apenas enquanto houver embarque aguardando.
     * Sem viagem ativa ou com estado final, não anuncia próximo embarque.
     */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PosicaoEmbarqueDTO posicaoMinha(Long ref,String uid){
        var operacao=buscar(TipoOperacao.VIAGEM,ref);
        var proprio=proprio(operacao,uid);
        boolean aguardando=ativa(operacao)&&proprio.estado==EstadoOperacional.AGUARDANDO;
        Long pendentes=aguardando
            ?registros.contarParadasPendentesAntes(operacao.id,EstadoOperacional.AGUARDANDO,proprio.ordem)
            :null;
        return new PosicaoEmbarqueDTO(ref,proprio.estado,proprio.ordem,pendentes,
            pendentes!=null&&pendentes==0L);
    }
    @Transactional(readOnly=true) public OperacaoDTO.Registro minhaWeb(Long ref,String token){return dto(web(buscar(TipoOperacao.EXCURSAO,ref),token));}
    /**
     * Descobre apenas execuções cujo snapshot contém a própria inscrição confirmada.
     * Não devolve passageiros, paradas, roteiro, coordenadas ou credenciais.
     */
    @Transactional(readOnly=true)
    public List<ExecucaoInscricaoDTO> execucoesWeb(String token){
        String hash;
        try{hash=TokensExcursao.hash(token);}catch(IllegalArgumentException ex){throw naoEncontrado();}
        var credencial=credenciais.findByTokenHash(hash)
                .filter(c->c.getExpiraEm().isAfter(clock.instant()))
                .orElseThrow(this::naoEncontrado);
        var inscricao=inscricoes.findByCredencialId(credencial.getId())
                .filter(i->i.getEstado()==StatusInscricaoExcursao.CONFIRMADA
                        &&i.getExcursao().getEstado()!=EstadoExcursao.CANCELADA
                        &&Objects.equals(i.getExcursao().getId(),credencial.getExcursao().getId()))
                .orElseThrow(this::naoEncontrado);
        return registros.findByInscricaoIdOrderByIdAsc(inscricao.getId()).stream()
                .map(r->r.operacao)
                .filter(o->o!=null&&o.tipo==TipoOperacao.EXCURSAO&&o.execucao!=null
                        &&o.execucao.getExcursao()!=null
                        &&Objects.equals(o.execucao.getExcursao().getId(),credencial.getExcursao().getId()))
                .map(o->new ExecucaoInscricaoDTO(o.execucao.getId(),o.execucao.getSentido(),
                        o.execucao.getEstado(),o.execucao.getIniciadaEm()))
                .distinct()
                .sorted(Comparator.comparing(ExecucaoInscricaoDTO::id))
                .toList();
    }
    @Transactional public OperacaoDTO.Registro alterarDono(TipoOperacao tipo,Long ref,Long registro,String uid,OperacaoDTO.Alteracao d){var o=buscar(tipo,ref);dono(o,uid);lock(o);var r=registros.findById(registro).filter(v->v.operacao.id.equals(o.id)).orElseThrow(this::naoEncontrado);return alterar(o,r,d,"USUARIO:"+usuario(uid).getId(),"MOTORISTA",true);}
    @Transactional public OperacaoDTO.Registro alterarMinha(Long ref,String uid,OperacaoDTO.Alteracao d){var o=buscar(TipoOperacao.VIAGEM,ref);lock(o);var r=proprio(o,uid);return alterar(o,r,d,"USUARIO:"+r.aluno.getId(),"PASSAGEIRO",false);}
    @Transactional public OperacaoDTO.Registro alterarWeb(Long ref,String token,OperacaoDTO.Alteracao d){var o=buscar(TipoOperacao.EXCURSAO,ref);lock(o);var r=web(o,token);return alterar(o,r,d,"INSCRICAO:"+r.inscricao.getId(),"WEB",false);}
    private OperacaoDTO.Registro alterar(OperacaoViagem o,RegistroOperacional r,OperacaoDTO.Alteracao d,String autor,String origem,boolean dono){
        em.refresh(r);if(d==null||d.estado()==null)throw new IllegalArgumentException("Informe o estado.");boolean corrigir=Boolean.TRUE.equals(d.correcao());
        if(corrigir&&!dono)throw new AccessDeniedException("Somente motorista corrige.");
        if(r.estado==d.estado())return dto(r);if(!ativa(o))throw conflito("Viagem encerrada.");
        if(d.versao()==null||d.versao()!=r.versao)throw conflito("Registro mudou; atualize a versão.");
        if(corrigir){if(d.motivo()==null||d.motivo().isBlank()||d.motivo().length()>500)throw new IllegalArgumentException("Informe motivo da correção (até 500 caracteres).");}
        else if(!((r.estado==EstadoOperacional.AGUARDANDO&&(d.estado()==EstadoOperacional.EMBARCADO||d.estado()==EstadoOperacional.NAO_EMBARCOU))||(r.estado==EstadoOperacional.EMBARCADO&&d.estado()==EstadoOperacional.DESEMBARCADO)))throw conflito("Transição inválida; solicite correção ao motorista.");
        if(d.estado()==EstadoOperacional.DESEMBARCADO&&r.estado!=EstadoOperacional.EMBARCADO)throw conflito("Desembarque exige embarque válido anterior.");
        var anterior=r.estado;r.estado=d.estado();r.autor=autor;r.origem=origem;r.atualizadoEm=clock.instant();registros.saveAndFlush(r);
        var e=new EventoOperacional();e.registro=r;e.anterior=anterior;e.novo=r.estado;e.autor=autor;e.origem=origem;e.criadoEm=r.atualizadoEm;e.correcao=corrigir;e.motivo=corrigir?d.motivo().trim():null;e.versaoRegistro=r.versao;eventos.saveAndFlush(e);
        publisher.publishEvent(new EventosDeViagem.Operacao(o.id,r.id,corrigir?"ESTADO_CORRIGIDO":r.estado==EstadoOperacional.EMBARCADO?"EMBARQUE_CONFIRMADO":r.estado==EstadoOperacional.DESEMBARCADO?"DESEMBARQUE_CONFIRMADO":"NAO_EMBARCOU","REGISTRO:"+r.id+":"+r.versao));return dto(r);
    }
    @Transactional(readOnly=true) public List<OperacaoDTO.Evento> historico(TipoOperacao tipo,Long ref,Long registro,String uid){var o=buscar(tipo,ref);dono(o,uid);var r=registros.findById(registro).filter(v->v.operacao.id.equals(o.id)).orElseThrow(this::naoEncontrado);return historico(r);}
    @Transactional(readOnly=true) public List<OperacaoDTO.Evento> historicoMeu(Long ref,String uid){return historico(proprio(buscar(TipoOperacao.VIAGEM,ref),uid));}
    @Transactional(readOnly=true) public List<OperacaoDTO.Evento> historicoWeb(Long ref,String token){return historico(web(buscar(TipoOperacao.EXCURSAO,ref),token));}
    private List<OperacaoDTO.Evento> historico(RegistroOperacional r){return eventos.findByRegistroIdOrderByIdAsc(r.id).stream().map(e->new OperacaoDTO.Evento(e.id,e.anterior,e.novo,e.autor,e.origem,e.criadoEm,e.correcao,e.motivo,e.versaoRegistro)).toList();}
    private OperacaoDTO.Registro dto(RegistroOperacional r){return new OperacaoDTO.Registro(r.id,r.aluno==null?null:r.aluno.getId(),r.inscricao==null?null:r.inscricao.getId(),r.nome,r.estado,r.versao,r.autor,r.origem,r.atualizadoEm,r.paradaId,r.ordem);}
    @Transactional(readOnly=true) public List<PassageiroRotaDTO> rotaAtiva(Long turma,String sentido){
        var v=viagens.findFirstByTurmaIdAndStatus(turma,"EM_ANDAMENTO").orElse(null);if(v==null)return null;
        if(!sentido.equals(v.getSentido()))throw conflito("A viagem ativa utiliza outro sentido. Encerre-a antes de planejar a próxima.");
        var o=operacoes.findByTipoAndReferenciaId(TipoOperacao.VIAGEM,v.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Snapshot operacional da viagem ativa indisponível."));
        return registros.findByOperacaoIdOrderByOrdemAscIdAsc(o.id).stream().map(r->new PassageiroRotaDTO(r.aluno.getId(),r.nome,r.latitude,r.longitude)).toList();
    }
}
