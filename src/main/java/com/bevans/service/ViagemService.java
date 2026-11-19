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
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private java.time.Clock clock=java.time.Clock.system(java.time.ZoneId.of("America/Recife"));

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

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.bevans.repository.AlunoTurmaRepository vinculos;

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.bevans.repository.RotaPublicaRepository rotasPublicas;

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.bevans.repository.PresencaRepository presencas;

    @Transactional
    public void iniciarRota(Long turmaId, String sentido) {
        iniciarRota(turmaId,sentido,"PRIVADA",null);
    }

    @Transactional
    public void iniciarRota(Long turmaId, String sentido, String modalidade, Long rotaPublicaId) {
        if(!"IDA".equals(sentido)&&!"VOLTA".equals(sentido))throw new IllegalArgumentException("Informe IDA ou VOLTA.");
        if("PRIVADA".equals(modalidade)){
            if(rotaPublicaId!=null)throw new IllegalArgumentException("Viagem privada não aceita ID de publicação pública.");
        }else if("PUBLICA".equals(modalidade)){
            if(rotaPublicaId==null||rotasPublicas==null)
                throw new IllegalArgumentException("Informe a rota pública da viagem.");
        }else throw new IllegalArgumentException("Informe modalidade PRIVADA ou PUBLICA.");
        Turma turma = turmaRepository.lockById(turmaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Turma não encontrada no sistema."));

        Optional<Viagem> viagemAtiva = viagemRepository.findFirstByTurmaIdAndStatus(turmaId, "EM_ANDAMENTO");
        
        if (viagemAtiva.isPresent()) {
            throw new ConflitoException("Já existe uma rota em andamento exclusivamente para esta turma.");
        }

        // Valida a rota pública após bloquear a turma. A publicação e
        // disponibilidade também são alteradas sob esse bloqueio.
        if ("PUBLICA".equals(modalidade)) {
            var rota = rotasPublicas.findById(rotaPublicaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                    "Rota pública não encontrada."));

            if (rota.getTurma() == null ||
                    !java.util.Objects.equals(rota.getTurma().getId(), turmaId))
                throw new IllegalArgumentException(
                    "Rota pública não pertence à turma informada.");

            if (!rota.getPublicada() || !rota.getAtiva())
                throw new ConflitoException(
                    "Publique e ative a rota pública antes de iniciar.");

            Integer capacidade = turmaRepository.capacidadeAtual(turmaId);
            if (capacidade == null || capacidade < 1)
                throw new ConflitoException(
                    "Configure a capacidade antes de iniciar a rota pública.");

            var hoje = java.time.LocalDate.now(clock);
            if (rota.getDias() == null ||
                    !rota.getDias().contains(hoje.getDayOfWeek()))
                throw new ConflitoException(
                    "A rota pública não opera no dia de hoje.");

            var horario = "IDA".equals(sentido)
                ? rota.getHorarioIda() : rota.getHorarioVolta();

            if (horario == null)
                throw new ConflitoException(
                    "Configure o horário de " + sentido +
                    " antes de iniciar a rota pública.");
        }
        // Rotas privadas: não iniciar silenciosamente com passageiros
        // aprovados que ainda não confirmaram a presença do dia.
        // Rotas públicas usam solicitações de embarque próprias.
        if ("PRIVADA".equals(modalidade) && vinculos != null && presencas != null
                && vinculos.countByTurmaIdAndStatus(turmaId, "APROVADO") > 0) {
            java.time.LocalDate data = java.time.LocalDate.now(clock);
            var confirmacoes = presencas.findByDataAndTurmaId(data, turmaId);
            var registrados = confirmacoes.stream()
                .map(p -> p.getUsuario().getId())
                .collect(java.util.stream.Collectors.toSet());

            var semConfirmacao = vinculos.idsAprovados(turmaId)
                .stream()
                .filter(id -> !registrados.contains(id))
                .toList();

            if (!semConfirmacao.isEmpty()) {
                throw new ConflitoException(
                    "Existem " + semConfirmacao.size()
                    + " passageiros aprovados sem confirmação "
                    + "para hoje. Confirme a presença e o ponto "
                    + "de embarque, ou registre NAO_VOU."
                );
            }
        }
        if("PRIVADA".equals(modalidade)&&coletas!=null&&coletas.getIfAvailable()!=null)
            coletas.getObject().validarInicio(turmaId,sentido);

        Viagem novaViagem = new Viagem();
        novaViagem.setTurma(turma);
        novaViagem.setSentido(sentido);
        novaViagem.setStatus("EM_ANDAMENTO");
        novaViagem.definirModalidade(modalidade,rotaPublicaId);
        novaViagem.registrarInicio(clock.instant(),clock.getZone());

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
            resposta.put("estado", "EM_ANDAMENTO");
            resposta.put("viagemId",v.getId());
            resposta.put("sentido", v.getSentido());
            resposta.put("modalidade",v.getModalidade());
            if(v.getRotaPublicaId()!=null)resposta.put("rotaPublicaId",v.getRotaPublicaId());
            resposta.put("turmaId", v.getTurma().getId());
            resposta.put("turmaNome", v.getTurma().getNome());
        } else {
            resposta.put("status", "INATIVA");
            var ultima=viagemRepository.findFirstByTurmaIdOrderByIdDesc(turmaId).orElse(null);
            resposta.put("estado",ultima==null?"NAO_INICIADA":ultima.getStatus());
            if(ultima!=null)resposta.put("ultimaViagemId",ultima.getId());
        }
        return resposta;
    }
}
