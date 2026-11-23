package com.bevans.service;

import com.bevans.dto.ExcursaoPassageiroDTO;
import com.bevans.dto.PaginaDTO;
import com.bevans.model.InscricaoExcursao;
import com.bevans.model.EstadoExcursao;
import com.bevans.model.StatusInscricaoExcursao;
import com.bevans.model.Usuario;
import com.bevans.repository.CredencialInscricaoExcursaoRepository;
import com.bevans.repository.InscricaoExcursaoRepository;
import com.bevans.repository.UsuarioRepository;
import java.time.Clock;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExcursaoPassageiroService {
    private final UsuarioRepository usuarios;
    private final InscricaoExcursaoRepository inscricoes;
    private final CredencialInscricaoExcursaoRepository credenciais;
    private final Clock clock;

    public ExcursaoPassageiroService(
            UsuarioRepository usuarios,
            InscricaoExcursaoRepository inscricoes,
            CredencialInscricaoExcursaoRepository credenciais,
            Clock clock) {
        this.usuarios = usuarios;
        this.inscricoes = inscricoes;
        this.credenciais = credenciais;
        this.clock = clock;
    }

    private Usuario passageiro(String uid) {
        if (uid == null || uid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return usuarios.findByFirebaseUid(uid)
            .filter(u -> "PASSAGEIRO".equals(u.getTipo()))
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    private ResponseStatusException indisponivel() {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Inscrição indisponível.");
    }

    @Transactional
    public void vincular(String uid, String token) {
        Usuario usuario = passageiro(uid);

        String hash;
        try {
            hash = TokensExcursao.hash(token);
        } catch (IllegalArgumentException ex) {
            throw indisponivel();
        }

        var credencial = credenciais.findByTokenHash(hash)
            .orElseThrow(this::indisponivel);

        if (!credencial.getExpiraEm().isAfter(clock.instant())) {
            throw indisponivel();
        }

        InscricaoExcursao inscricao = inscricoes
            .lockByCredencialId(credencial.getId())
            .orElseThrow(this::indisponivel);

        if (!credencial.getExpiraEm().isAfter(clock.instant())) {
            throw indisponivel();
        }

        if (inscricao.getPassageiro() != null) {
            if (Objects.equals(
                    inscricao.getPassageiro().getId(), usuario.getId())) {
                return;
            }

            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Inscrição já vinculada.");
        }

        inscricao.vincularPassageiro(usuario);
        inscricoes.save(inscricao);
    }

    @Transactional(readOnly = true)
    public ExcursaoPassageiroDTO consultar(String uid, Long inscricaoId) {
        Usuario usuario = passageiro(uid);

        var inscricao = inscricoes.findByIdAndPassageiroId(
            inscricaoId, usuario.getId()
        ).orElseThrow(this::indisponivel);

        return dto(inscricao);
    }

    private ExcursaoPassageiroDTO dto(InscricaoExcursao i) {
        var e = i.getExcursao();

        StatusInscricaoExcursao estado =
            e.getEstado() == EstadoExcursao.CANCELADA
                ? StatusInscricaoExcursao.CANCELADA
                : i.getEstado();

        if (estado == StatusInscricaoExcursao.SOLICITADA
                && !i.getReservaValidaAte().isAfter(clock.instant())) {
            estado = StatusInscricaoExcursao.EXPIRADA;
        }

        return new ExcursaoPassageiroDTO(
            i.getId(),
            e.getId(),
            e.getTitulo(),
            e.getDestinoNome(),
            e.getPartidaEm(),
            e.getRetornoEm(),
            e.getEstado(),
            estado
        );
    }

    @Transactional(readOnly = true)
    public PaginaDTO<ExcursaoPassageiroDTO> minhas(
            String uid, int pagina, int tamanho) {
        Usuario usuario = passageiro(uid);

        if (pagina < 0 || pagina > 10000 ||
                tamanho < 1 || tamanho > 50) {
            throw new IllegalArgumentException("Paginação inválida.");
        }

        var resultado = inscricoes.findByPassageiroId(
            usuario.getId(),
            PageRequest.of(pagina, tamanho,
                Sort.by(Sort.Direction.DESC, "id")));

        var itens = resultado.getContent().stream()
            .map(this::dto)
            .toList();

        return new PaginaDTO<>(
            itens, resultado.getTotalElements(),
            pagina, tamanho, resultado.getTotalPages());
    }
}
