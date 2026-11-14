package com.bevans.service;
import com.bevans.dto.ContatoMotoristaDTO;
import com.bevans.model.*;
import com.bevans.repository.*;
import jakarta.persistence.*;
import java.time.Clock;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ContatoMotoristaService {
    private final UsuarioRepository usuarios;private final ContatoMotoristaRepository contatos;
    private final TurmaRepository turmas;private final RotaPublicaRepository rotas;private final AlunoTurmaRepository matriculas;
    private final CredencialInscricaoExcursaoRepository credenciais;private final InscricaoExcursaoRepository inscricoes;
    private final EntityManager em;private final Clock clock;
    private static final Set<String> DDDS=Set.of("11","12","13","14","15","16","17","18","19","21","22","24","27","28","31","32","33","34","35","37","38","41","42","43","44","45","46","47","48","49","51","53","54","55","61","62","63","64","65","66","67","68","69","71","73","74","75","77","79","81","82","83","84","85","86","87","88","89","91","92","93","94","95","96","97","98","99");
    public ContatoMotoristaService(UsuarioRepository u,ContatoMotoristaRepository c,TurmaRepository t,RotaPublicaRepository r,AlunoTurmaRepository a,CredencialInscricaoExcursaoRepository cr,InscricaoExcursaoRepository i,EntityManager em,Clock clock){usuarios=u;contatos=c;turmas=t;rotas=r;matriculas=a;credenciais=cr;inscricoes=i;this.em=em;this.clock=clock;}
    private Usuario usuario(String uid){if(uid==null||uid.isBlank())throw new AccessDeniedException("Sessão obrigatória");return usuarios.findByFirebaseUid(uid).orElseThrow(()->new AccessDeniedException("Perfil indisponível"));}
    private ResponseStatusException ausente(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Contato indisponível");}
    private void motorista(Usuario u){if(!"MOTORISTA".equals(u.getTipo()))throw new AccessDeniedException("Somente motorista configura contato");}
    static String normalizar(String entrada){
        if(entrada==null||entrada.length()>40||!entrada.matches("\\+?[0-9 ().-]+"))throw new IllegalArgumentException("Informe telefone comercial brasileiro com DDI 55 e DDD");
        String n=entrada.replaceAll("[ ().-]","");if(n.startsWith("+"))n=n.substring(1);
        if(!n.matches("55[1-9][0-9](?:[2-5][0-9]{7}|9[0-9]{8})")||!DDDS.contains(n.substring(2,4)))throw new IllegalArgumentException("Telefone/DDD inválido; informe DDI 55");return n;
    }
    @Transactional(readOnly=true) public ContatoMotoristaDTO.Configuracao minha(String uid){var u=usuario(uid);motorista(u);var c=contatos.findById(u.getId()).orElse(null);return c==null?new ContatoMotoristaDTO.Configuracao(null,false,null,0):configuracao(c);}
    @Transactional public ContatoMotoristaDTO.Configuracao configurar(String uid,ContatoMotoristaDTO.Configurar d){
        var u=usuario(uid);motorista(u);u=usuarios.lockById(u.getId()).orElseThrow();em.refresh(u,LockModeType.PESSIMISTIC_WRITE);motorista(u);
        if(d==null||d.habilitado()==null||d.versao()==null)throw new IllegalArgumentException("Informe habilitação e versão");
        // Revisão pública zero representa ausência; criação já invalida uma leitura anterior sem configuração.
        var c=contatos.findById(u.getId()).orElse(null);if(d.versao()!=(c==null?0:c.versao+1))throw new ResponseStatusException(HttpStatus.CONFLICT,"Contato mudou; atualize a versão");
        String telefone=d.telefone()==null?(c==null?null:c.telefoneComercial):normalizar(d.telefone());
        if(d.habilitado()&&(!Boolean.TRUE.equals(d.aceiteDivulgacao())||telefone==null))throw new IllegalArgumentException("Divulgação exige telefone e aceite explícito");
        if(c==null){c=new ContatoMotorista();c.motoristaId=u.getId();}
        if(c.habilitado==d.habilitado()&&java.util.Objects.equals(c.telefoneComercial,telefone)&&c.atualizadoEm!=null)return configuracao(c);
        c.telefoneComercial=telefone;c.habilitado=d.habilitado();c.atualizadoEm=clock.instant();if(c.habilitado)c.consentimentoEm=clock.instant();
        contatos.saveAndFlush(c);return configuracao(c);
    }
    @Transactional public ContatoMotoristaDTO turma(Long id,String uid){
        var u=usuario(uid);var t=turmas.findById(id).orElseThrow(this::ausente);
        if(!"PASSAGEIRO".equals(u.getTipo())||!matriculas.existsByTurmaIdAndAlunoIdAndStatus(id,u.getId(),"APROVADO"))throw ausente();
        return disponibilizar(t.getMotorista().getId());
    }
    @Transactional public ContatoMotoristaDTO rota(Long id,String uid){var r=rotas.findById(id).orElseThrow(this::ausente);return turma(r.getTurma().getId(),uid);}
    @Transactional public ContatoMotoristaDTO inscricao(String token){
        String hash;try{hash=TokensExcursao.hash(token);}catch(IllegalArgumentException ex){throw ausente();}
        var c=credenciais.findByTokenHash(hash).filter(v->v.getExpiraEm().isAfter(clock.instant())).orElseThrow(this::ausente);
        var i=inscricoes.findByCredencialId(c.getId()).filter(v->v.getEstado()==StatusInscricaoExcursao.CONFIRMADA&&v.getExcursao().getEstado()==EstadoExcursao.PUBLICADA).orElseThrow(this::ausente);
        return disponibilizar(i.getExcursao().getOrganizador().getId());
    }
    private ContatoMotoristaDTO disponibilizar(Long motorista){
        var u=usuarios.lockById(motorista).orElseThrow(this::ausente);em.refresh(u,LockModeType.PESSIMISTIC_WRITE);motorista(u);
        var c=contatos.findById(motorista).filter(v->v.habilitado&&v.consentimentoEm!=null&&v.telefoneComercial!=null).orElseThrow(this::ausente);
        return new ContatoMotoristaDTO(u.getId(),u.getNome(),c.telefoneComercial,"https://wa.me/"+c.telefoneComercial);
    }
    private ContatoMotoristaDTO.Configuracao configuracao(ContatoMotorista c){return new ContatoMotoristaDTO.Configuracao(c.telefoneComercial,c.habilitado,c.consentimentoEm,c.versao+1);}
}
