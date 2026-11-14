package com.bevans.service;
import com.bevans.repository.AlunoTurmaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
@Service
public class AvisosConfiguracaoService {
    private final AlunoTurmaRepository vinculos;private final ApplicationEventPublisher eventos;
    public AvisosConfiguracaoService(AlunoTurmaRepository v,ApplicationEventPublisher e){vinculos=v;eventos=e;}
    public void turma(Long turma,String contexto,Long referencia,String tipo){
        String chave="CONFIGURACAO:"+java.util.UUID.randomUUID();
        for(var v:vinculos.findByTurmaIdAndStatus(turma,"APROVADO"))eventos.publishEvent(new EventosDeViagem.Direto(tipo,contexto,referencia,v.getAluno().getId(),null,chave+":"+v.getAluno().getId()));
    }
}
