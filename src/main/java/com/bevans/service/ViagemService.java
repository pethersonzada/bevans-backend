package com.bevans.service;

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

    public ViagemService(ViagemRepository viagemRepository, TurmaRepository turmaRepository,com.bevans.repository.PosicaoViagemRepository posicoes) {
        this.viagemRepository = viagemRepository;
        this.turmaRepository = turmaRepository;
        this.posicoes=posicoes;
    }

    @Transactional
    public void iniciarRota(Long turmaId, String sentido) {
        Turma turma = turmaRepository.lockById(turmaId)
                .orElseThrow(() -> new RuntimeException("Turma não encontrada no sistema."));

        Optional<Viagem> viagemAtiva = viagemRepository.findFirstByTurmaIdAndStatus(turmaId, "EM_ANDAMENTO");
        
        if (viagemAtiva.isPresent()) {
            throw new RuntimeException("Já existe uma rota em andamento exclusivamente para esta turma.");
        }

        Viagem novaViagem = new Viagem();
        novaViagem.setTurma(turma);
        novaViagem.setSentido(sentido);
        novaViagem.setStatus("EM_ANDAMENTO");

        viagemRepository.save(novaViagem);
    }

    @Transactional
    public void encerrarRota(Long turmaId) {
        turmaRepository.lockById(turmaId).orElseThrow(()->new IllegalArgumentException("Turma não encontrada."));
        Optional<Viagem> viagemAtiva = viagemRepository.findFirstByTurmaIdAndStatus(turmaId, "EM_ANDAMENTO");
        
        if (viagemAtiva.isPresent()) {
            Viagem viagem = viagemAtiva.get();
            viagem.setStatus("FINALIZADA");
            posicoes.findById(viagem.getId()).ifPresent(p->{p.limpar();posicoes.save(p);});
            viagemRepository.save(viagem);
        }
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
