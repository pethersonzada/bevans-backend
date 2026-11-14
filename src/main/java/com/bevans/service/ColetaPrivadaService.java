package com.bevans.service;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ColetaPrivadaService {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private AvisosConfiguracaoService avisos;
    private final ColetaPrivadaRepository coletas;private final ParadaPrivadaRepository paradas;private final EscolhaParadaPrivadaRepository escolhas;
    private final TurmaRepository turmas;private final UsuarioRepository usuarios;private final AlunoTurmaRepository vinculos;
    private final EnderecoRepository enderecos;private final ViagemRepository viagens;private final PresencaRepository presencas;
    private final RotaPublicaRepository publicas;private final PosicaoViagemService gps;private final EntityManager em;private final Clock clock;private final ObjectProvider<RotaService> rotas;
    public ColetaPrivadaService(ColetaPrivadaRepository c,ParadaPrivadaRepository p,EscolhaParadaPrivadaRepository e,TurmaRepository t,
            UsuarioRepository u,AlunoTurmaRepository v,EnderecoRepository a,ViagemRepository j,PresencaRepository r,RotaPublicaRepository b,
            PosicaoViagemService g,EntityManager em,Clock clock,ObjectProvider<RotaService> rotas){coletas=c;paradas=p;escolhas=e;turmas=t;usuarios=u;vinculos=v;enderecos=a;viagens=j;presencas=r;publicas=b;gps=g;this.em=em;this.clock=clock;this.rotas=rotas;}
    private ResponseStatusException ausente(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Turma ou parada não encontrada.");}
    private ResponseStatusException conflito(String s){return new ResponseStatusException(HttpStatus.CONFLICT,s);}
    private void sentido(String s){if(!"IDA".equals(s)&&!"VOLTA".equals(s))throw new IllegalArgumentException("Informe IDA ou VOLTA.");}
    private Usuario usuario(String uid){return usuarios.findByFirebaseUid(uid).orElseThrow(()->new AccessDeniedException("Perfil inválido."));}
    private boolean dono(Turma t,Usuario u){return "MOTORISTA".equals(u.getTipo())&&t.getMotorista()!=null&&Objects.equals(t.getMotorista().getId(),u.getId());}
    private void aprovado(Long turma,Long aluno){if(!vinculos.existsByTurmaIdAndAlunoIdAndStatus(turma,aluno,"APROVADO"))throw new AccessDeniedException("Passageiro sem vínculo aprovado.");}
    private Turma turma(Long id,String uid,boolean motorista,boolean lock){
        Usuario u=usuario(uid);Turma t=(lock?turmas.lockById(id):turmas.findById(id)).orElseThrow(this::ausente);
        if(lock)em.refresh(t,LockModeType.PESSIMISTIC_WRITE);
        if(t.isExcluido())throw ausente();if(publicas.findByTurmaId(id).isPresent())throw conflito("Esta configuração pertence somente a turmas privadas.");
        if(!dono(t,u)){if(motorista||!"PASSAGEIRO".equals(u.getTipo()))throw new AccessDeniedException("Turma de outro motorista.");aprovado(id,u.getId());}return t;
    }
    private void inativa(Long turma){if(viagens.existsByTurmaIdAndStatus(turma,"EM_ANDAMENTO"))throw conflito("Não altere coleta ou escolhas durante viagem ativa.");}
    private void semPlanos(Long turma){if(presencas.possuiPlanejamento(turma,LocalDate.now(clock)))throw conflito("Há presenças planejadas: ajuste-as explicitamente antes de mudar a coleta.");}
    private ColetaPrivada coleta(Long id,String s){sentido(s);return coletas.findByTurmaIdAndSentido(id,s).orElse(null);}
    private List<ParadaPrivada> ativas(ColetaPrivada c){return c==null?List.of():paradas.findByColetaIdOrderByOrdemAscIdAsc(c.getId()).stream().filter(ParadaPrivada::isAtiva).toList();}
    private void coordenadas(Double lat,Double lon){if(lat==null||lon==null||!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180||(lat==0&&lon==0))throw new IllegalArgumentException("Confirme coordenadas válidas.");}
    @Transactional public ColetaPrivadaDTO configurar(Long id,String s,String uid,ColetaPrivadaRequest d){
        Turma t=turma(id,uid,true,true);sentido(s);inativa(id);semPlanos(id);ColetaPrivada c=coleta(id,s);
        if(d==null||d.modo()==null||d.paradas()==null||d.versao()==null)throw new IllegalArgumentException("Informe modo, versão e paradas.");
        if(d.versao()!=(c==null?0:c.getRevisao()))throw conflito("Configuração mudou; atualize a versão.");
        if(d.paradas().size()>50||(d.modo()==ModoColetaPrivada.PORTA_A_PORTA&&!d.paradas().isEmpty())||(d.modo()==ModoColetaPrivada.PARADAS_DEFINIDAS&&d.paradas().isEmpty()))throw new IllegalArgumentException("Quantidade de paradas incompatível (1–50 no modo de paradas).");
        var existentes=c==null?List.<ParadaPrivada>of():paradas.findByColetaIdOrderByOrdemAscIdAsc(c.getId());var porId=new HashMap<Long,ParadaPrivada>();existentes.forEach(p->porId.put(p.getId(),p));var ids=new HashSet<Long>();
        for(var p:d.paradas()){
            if(p==null||p.nome()==null||p.nome().isBlank()||p.nome().trim().length()>120||!Boolean.TRUE.equals(p.pontoConfirmado()))throw new IllegalArgumentException("Confirme nome e pin de cada parada.");
            coordenadas(p.latitude(),p.longitude());if(p.id()!=null&&(!porId.containsKey(p.id())||!ids.add(p.id())))throw new IllegalArgumentException("Parada duplicada ou de outra turma/sentido.");
        }
        if(c!=null)for(var e:escolhas.findByColetaIdAndAtivaTrue(c.getId())){
            var nova=d.paradas().stream().filter(p->Objects.equals(p.id(),e.getParada().getId())).findFirst().orElse(null);var antiga=e.getParada();
            if(d.modo()!=c.getModo()||nova==null||nova.latitude()!=antiga.getLatitude()||nova.longitude()!=antiga.getLongitude()||!Objects.equals(nova.horario(),antiga.getHorario()))throw conflito("Parada utilizada: remova ou reconcilie as escolhas antes de alterá-la.");
        }
        var antigas=existentes.stream().filter(ParadaPrivada::isAtiva).map(p->new ColetaPrivadaRequest.Parada(p.getId(),p.getNome(),p.getLatitude(),p.getLongitude(),p.getHorario(),true)).toList();
        var novas=d.paradas().stream().map(p->new ColetaPrivadaRequest.Parada(p.id(),p.nome().trim(),p.latitude(),p.longitude(),p.horario(),true)).toList();
        boolean mudou=(c==null?ModoColetaPrivada.PORTA_A_PORTA:c.getModo())!=d.modo()||!antigas.stream().map(p->new ColetaPrivadaRequest.Parada(p.id(),p.nome(),p.latitude(),p.longitude(),null,true)).toList().equals(novas.stream().map(p->new ColetaPrivadaRequest.Parada(p.id(),p.nome(),p.latitude(),p.longitude(),null,true)).toList());
        boolean horariosMudaram=!antigas.stream().filter(p->p.horario()!=null).map(p->java.util.Arrays.asList(p.id(),p.horario())).toList().equals(novas.stream().filter(p->p.horario()!=null).map(p->java.util.Arrays.asList(p.id(),p.horario())).toList());
        if(c==null){c=new ColetaPrivada();c.criar(t,s);c=coletas.saveAndFlush(c);}
        existentes.forEach(ParadaPrivada::desativar);int ordem=0;
        for(var p:d.paradas()){var a=p.id()==null?new ParadaPrivada():porId.get(p.id());a.configurar(c,p.nome().trim(),p.latitude(),p.longitude(),ordem++,p.horario());paradas.save(a);}
        paradas.flush();c.alterar(d.modo());coletas.saveAndFlush(c);
        if(mudou&&avisos!=null)avisos.turma(id,"TURMA",id,"TRAJETO_ALTERADO");
        if(horariosMudaram&&avisos!=null)avisos.turma(id,"TURMA",id,"HORARIO_ALTERADO");
        return consultar(id,s,uid,null);
    }
    @Transactional public void escolher(Long id,String s,String uid,Long alunoId,Long paradaId,Long versao){
        Turma t=turma(id,uid,false,true);Usuario u=usuario(uid);Long aluno=alunoId==null?u.getId():alunoId;
        if(!dono(t,u)&&!Objects.equals(aluno,u.getId()))throw new AccessDeniedException("Escolha de outro passageiro.");aprovado(id,aluno);inativa(id);semPlanos(id);
        var c=coleta(id,s);if(c==null||c.getModo()!=ModoColetaPrivada.PARADAS_DEFINIDAS)throw conflito("Turma não usa paradas neste sentido.");
        if(versao==null||versao!=c.getRevisao())throw conflito("Paradas mudaram; atualize a versão.");
        var p=ativas(c).stream().filter(v->v.getId().equals(paradaId)).findFirst().orElseThrow(this::ausente);
        var e=escolhas.findByColetaIdAndAlunoId(c.getId(),aluno).orElseGet(EscolhaParadaPrivada::new);e.escolher(c,usuarios.findById(aluno).orElseThrow(this::ausente),p);escolhas.saveAndFlush(e);
    }
    @Transactional public void removerEscolha(Long id,String s,String uid,Long alunoId){
        Turma t=turma(id,uid,false,true);Usuario u=usuario(uid);Long aluno=alunoId==null?u.getId():alunoId;
        if(!dono(t,u)&&!Objects.equals(aluno,u.getId()))throw new AccessDeniedException("Escolha de outro passageiro.");inativa(id);semPlanos(id);var c=coleta(id,s);
        if(c!=null)escolhas.findByColetaIdAndAlunoId(c.getId(),aluno).ifPresent(e->{e.remover();escolhas.save(e);});
    }
    private ColetaPrivadaDTO.Parada dto(ParadaPrivada p){return new ColetaPrivadaDTO.Parada(p.getId(),p.getNome(),p.getLatitude(),p.getLongitude(),p.getOrdem(),p.getHorario());}
    public static double distancia(double a,double b,double c,double d){double x=Math.sin(Math.toRadians(c-a)/2),y=Math.sin(Math.toRadians(d-b)/2);double h=Math.min(1,Math.max(0,x*x+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*y*y));return 6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public ColetaPrivadaDTO consultar(Long id,String s,String uid,Long enderecoId){
        turma(id,uid,false,false);var u=usuario(uid);var c=coleta(id,s);var ps=ativas(c);Long escolhida=null;ColetaPrivadaDTO.Sugestao sugestao=null;
        if("PASSAGEIRO".equals(u.getTipo())&&c!=null)escolhida=escolhas.findByColetaIdAndAlunoId(c.getId(),u.getId()).filter(EscolhaParadaPrivada::isAtiva).map(e->e.getParada().getId()).orElse(null);
        if(enderecoId!=null){
            var a=enderecos.findById(enderecoId).orElseThrow(this::ausente);
            if(!"PASSAGEIRO".equals(u.getTipo())||a.getUsuario()==null||!u.getId().equals(a.getUsuario().getId()))throw new AccessDeniedException("Endereço de outro titular.");
            coordenadas(a.getLatitude(),a.getLongitude());
            var p=ps.stream().min(Comparator.<ParadaPrivada>comparingDouble(v->distancia(a.getLatitude(),a.getLongitude(),v.getLatitude(),v.getLongitude())).thenComparing(ParadaPrivada::getId)).orElse(null);
            if(p!=null)sugestao=new ColetaPrivadaDTO.Sugestao(dto(p),distancia(a.getLatitude(),a.getLongitude(),p.getLatitude(),p.getLongitude()),"HAVERSINE_LINHA_RETA");
        }
        var v=viagens.findFirstByTurmaIdAndStatus(id,"EM_ANDAMENTO").filter(x->s.equals(x.getSentido())).orElse(null);
        return new ColetaPrivadaDTO(id,s,c==null?ModoColetaPrivada.PORTA_A_PORTA:c.getModo(),c==null?0:c.getRevisao(),ps.stream().map(this::dto).toList(),escolhida,sugestao,v==null?null:v.getId(),v==null?null:gps.consultar(v.getId(),uid));
    }
    @Transactional(readOnly=true) public boolean usaParadas(Long id,String s){var c=coleta(id,s);return c!=null&&c.getModo()==ModoColetaPrivada.PARADAS_DEFINIDAS;}
    @Transactional public void prepararPresenca(Long id,Long aluno,String status){
        turmas.lockById(id).orElseThrow(this::ausente);inativa(id);aprovado(id,aluno);
        for(String s:List.of("IDA","VOLTA"))if(PresencasEsperadas.inclui(status,s)&&usaParadas(id,s)){
            var c=coleta(id,s);if(escolhas.findByColetaIdAndAlunoId(c.getId(),aluno).filter(EscolhaParadaPrivada::isAtiva).filter(e->e.getParada().isAtiva()).isEmpty())throw conflito("Escolha uma parada para "+s+" antes de confirmar presença.");
        }
    }
    @Transactional(readOnly=true) public List<PontoColetaPrivadaDTO> planejar(Long id,String s,String uid,boolean otimizar){turma(id,uid,true,false);return roteiroSistema(id,s,otimizar);}
    @Transactional(readOnly=true) public List<PontoColetaPrivadaDTO> roteiroSistema(Long id,String s,boolean otimizar){
        sentido(s);var t=turmas.findById(id).orElseThrow(this::ausente);coordenadas(t.getOrigemLatitude(),t.getOrigemLongitude());coordenadas(t.getDestinoLatitude(),t.getDestinoLongitude());
        var pontos=new ArrayList<PontoColetaPrivadaDTO>();boolean volta="VOLTA".equals(s);
        pontos.add(new PontoColetaPrivadaDTO("ORIGEM",null,volta?t.getDestinoNome():t.getOrigemNome(),volta?t.getDestinoLatitude():t.getOrigemLatitude(),volta?t.getDestinoLongitude():t.getOrigemLongitude(),List.of()));
        if(!usaParadas(id,s)){
            for(var p:rotas.getObject().otimizarRota(s,id).join())pontos.add(new PontoColetaPrivadaDTO("INDIVIDUAL",null,p.getNome(),p.getLatitude(),p.getLongitude(),List.of(p)));
        }else{
            var c=coleta(id,s);var ps=new ArrayList<>(ativas(c));if(ps.isEmpty())throw conflito("Nenhuma parada ativa.");
            var porAluno=new HashMap<Long,EscolhaParadaPrivada>();escolhas.findByColetaIdAndAtivaTrue(c.getId()).forEach(e->porAluno.put(e.getAluno().getId(),e));
            var membros=new HashMap<Long,List<PassageiroRotaDTO>>();
            for(var p:presencas.findByDataAndTurmaId(LocalDate.now(clock),id))if(PresencasEsperadas.inclui(p.getStatus(),s)){
                var e=porAluno.get(p.getUsuario().getId());if(e==null||!e.getParada().isAtiva())throw conflito("Passageiro esperado sem parada válida.");var a=e.getParada();
                membros.computeIfAbsent(a.getId(),x->new ArrayList<>()).add(new PassageiroRotaDTO(p.getUsuario().getId(),p.getUsuario().getNome(),a.getLatitude(),a.getLongitude()));
            }
            if(volta)Collections.reverse(ps);for(var p:ps)pontos.add(new PontoColetaPrivadaDTO("PARADA",p.getId(),p.getNome(),p.getLatitude(),p.getLongitude(),List.copyOf(membros.getOrDefault(p.getId(),List.of()))));
        }
        pontos.add(new PontoColetaPrivadaDTO("DESTINO",null,volta?t.getOrigemNome():t.getDestinoNome(),volta?t.getOrigemLatitude():t.getDestinoLatitude(),volta?t.getOrigemLongitude():t.getDestinoLongitude(),List.of()));
        if(!otimizar||pontos.size()<3)return pontos;var entrada=new ArrayList<PontoPublicoDTO>();for(int n=0;n<pontos.size();n++){var p=pontos.get(n);entrada.add(new PontoPublicoDTO((long)n,p.nome(),p.latitude(),p.longitude()));}
        return OtimizadorParadas.ordenar(entrada).stream().map(p->pontos.get(Math.toIntExact(p.paradaId()))).toList();
    }
    @Transactional(readOnly=true) public void validarInicio(Long id,String s){if(usaParadas(id,s))roteiroSistema(id,s,false);}
}
