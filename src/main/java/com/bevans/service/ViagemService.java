package com.bevans.service;

import com.bevans.exception.ConflitoException;

import com.bevans.exception.RecursoNaoEncontradoException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bevans.model.Turma;
import com.bevans.model.Viagem;
import com.bevans.repository.TurmaRepository;
import com.bevans.repository.ViagemRepository;

@Service
public class ViagemService {

    private final ViagemRepository viagemRepository;
    private final TurmaRepository turmaRepository;
    private final com.bevans.repository.PosicaoViagemRepository posicoes;
    private org.springframework.beans.factory.ObjectProvider<ColetaPrivadaService> coletas;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher publisher;
    @org.springframework.beans.factory.annotation.Autowired
    public ViagemService(ViagemRepository v,TurmaRepository t,com.bevans.repository.PosicaoViagemRepository p,org.springframework.beans.factory.ObjectProvider<ColetaPrivadaService> c){this(v,t,p);coletas=c;}

    public ViagemService(ViagemRepository viagemRepository, TurmaRepository turmaRepository,com.bevans.repository.PosicaoViagemRepository posicoes) {
        this.viagemRepository = viagemRepository;
        this.turmaRepository = turmaRepository;
        this.posicoes=posicoes;
    }

    @Transactional
    public void iniciarRota(Long turmaId, String sentido) {
        Turma turma = turmaRepository.lockById(turmaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma não encontrada no sistema."));

        Optional<Viagem> viagemAtiva = viagemRepository.findFirstByTurmaIdAndStatus(turmaId, "EM_ANDAMENTO");
        
        if (viagemAtiva.isPresent()) {
            throw new ConflitoException("Já existe uma rota em andamento exclusivamente para esta turma.");
        }
        if(coletas!=null&&coletas.getIfAvailable()!=null)coletas.getObject().validarInicio(turmaId,sentido);

        Viagem novaViagem = new Viagem();
        novaViagem.setTurma(turma);
        novaViagem.setSentido(sentido);
        novaViagem.setStatus("EM_ANDAMENTO");

        viagemRepository.saveAndFlush(novaViagem);
        if(publisher!=null)publisher.publishEvent(new EventosDeViagem.Viagem("INICIADA",novaViagem.getId()));
    }

    @Transactional
    public void encerrarRota(Long turmaId) {
        turmaRepository.lockById(turmaId).orElseThrow(()->new RecursoNaoEncontradoException("Turma não encontrada."));
        Optional<Viagem> viagemAtiva = viagemRepository.findFirstByTurmaIdAndStatus(turmaId, "EM_ANDAMENTO");
        
        if (viagemAtiva.isPresent()) {
            Viagem viagem = viagemAtiva.get();
            if(publisher!=null)publisher.publishEvent(new EventosDeViagem.Viagem("VALIDAR_FIM",viagem.getId()));
            viagem.setStatus("FINALIZADA");
            posicoes.findById(viagem.getId()).ifPresent(p->{p.limpar();posicoes.save(p);});
            viagemRepository.save(viagem);
            if(publisher!=null)publisher.publishEvent(new EventosDeViagem.Viagem("FINALIZADA",viagem.getId()));
        }
    }

    @Transactional(readOnly = true)
    public boolean rotaAtiva(Long turmaId) {
        return viagemRepository.existsByTurmaIdAndStatus(
                turmaId, "EM_ANDAMENTO");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verificarStatusAtual(Long turmaId) {
        Optional<Viagem> viagemAtiva = viagemRepository.findFirstByTurmaIdAndStatus(turmaId, "EM_ANDAMENTO");
        Map<String, Object> resposta = new HashMap<>();

        if (viagemAtiva.isPresent()) {
            Viagem v = viagemAtiva.get();
            resposta.put("status", "ATIVA");
            resposta.put("viagemId",v.getId());
            resposta.put("sentido", v.getSentido());
            resposta.put("turmaId", v.getTurma().getId());
            resposta.put("turmaNome", v.getTurma().getNome());
        } else {
            resposta.put("status", "INATIVA");
        }
        return resposta;
    }
}
