package com.bevans.service;
import com.bevans.dto.NotificacaoDTO;
import com.bevans.repository.*;
import java.time.Clock;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class NotificacaoInscricaoService {
    private final CredencialInscricaoExcursaoRepository credenciais;private final InscricaoExcursaoRepository inscricoes;
    private final NotificacaoRepository notificacoes;private final Clock clock;
    public NotificacaoInscricaoService(CredencialInscricaoExcursaoRepository c,InscricaoExcursaoRepository i,NotificacaoRepository n,Clock t){credenciais=c;inscricoes=i;notificacoes=n;clock=t;}
    private Long inscricao(String token){
        String hash;try{hash=TokensExcursao.hash(token);}catch(IllegalArgumentException e){throw naoEncontrada();}
        var cred=credenciais.findByTokenHash(hash).filter(c->c.getExpiraEm().isAfter(clock.instant())).orElseThrow(this::naoEncontrada);
        return inscricoes.findByCredencialId(cred.getId()).orElseThrow(this::naoEncontrada).getId();
    }
    private ResponseStatusException naoEncontrada(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Inscrição não encontrada");}
    @Transactional(readOnly=true) public Page<NotificacaoDTO> listar(String token,int pagina,int tamanho){
        if(pagina<0||tamanho<1||tamanho>50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Paginação inválida");
        return notificacoes.findByInscricaoId(inscricao(token),PageRequest.of(pagina,tamanho,Sort.by(Sort.Direction.DESC,"id"))).map(n->new NotificacaoDTO(n.id,n.tipo,n.contextoTipo,n.contextoId,n.titulo,n.mensagem,n.criadaEm,n.lidaEm));
    }
    @Transactional public void ler(String token,Long id){var n=notificacoes.findByIdAndInscricaoId(id,inscricao(token)).orElseThrow(this::naoEncontrada);if(n.lidaEm==null)n.lidaEm=clock.instant();}
}
