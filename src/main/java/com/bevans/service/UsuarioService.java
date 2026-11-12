package com.bevans.service;

import com.bevans.exception.ConflitoException;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.firebase.auth.FirebaseAuth;
import com.bevans.exception.RecursoNaoEncontradoException;
import com.bevans.model.Usuario;
import com.bevans.repository.PresencaRepository;
import com.bevans.repository.UsuarioRepository;
import com.bevans.repository.ViagemRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PresencaRepository presencaRepository;
    private final ViagemRepository viagemRepository;

    public UsuarioService(UsuarioRepository usuarioRepository, PresencaRepository presencaRepository, ViagemRepository viagemRepository) {
        this.usuarioRepository = usuarioRepository;
        this.presencaRepository = presencaRepository;
        this.viagemRepository = viagemRepository;
    }

    public Usuario cadastrarUsuario(Usuario usuario) {
        if (usuarioRepository.findByEmail(usuario.getEmail()).isPresent()) {
            throw new ConflitoException("E-mail já cadastrado");
        }
        if (usuarioRepository.findByTelefone(usuario.getTelefone()).isPresent()) {
            throw new ConflitoException("Telefone já cadastrado");
        }
        
        return usuarioRepository.save(usuario);
    }

    public Usuario buscarPorId(Long id) {
        if (id == null) throw new IllegalArgumentException("id não pode ser nulo");
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    public List<Usuario> listarPassageiros() {
        return usuarioRepository.findAll()
                .stream()
                .filter(u -> "PASSAGEIRO".equals(u.getTipo()))
                .toList();
    }

    @Transactional
    public Usuario atualizarEmail(Long id, String novoEmail) {
        if (id == null) {
            throw new IllegalArgumentException("O ID não pode ser nulo.");
        }
        if (novoEmail == null || novoEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("O novo e-mail não pode estar vazio.");
        }

        if (usuarioRepository.findByEmail(novoEmail).isPresent()) {
            throw new ConflitoException("Este e-mail já está em uso por outro usuário.");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado no sistema."));

        usuario.setEmail(novoEmail.trim());
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void excluirUsuario(Long id) {
        if (id == null) throw new IllegalArgumentException("O ID fornecido para exclusão não pode ser nulo.");

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado no sistema."));
        
        if ("MOTORISTA".equals(usuario.getTipo())) {
            if (viagemRepository.existsByTurmaMotoristaIdAndStatus(id, "EM_ANDAMENTO")) {
                throw new ConflitoException("Não é possível excluir a conta com uma rota em andamento. Encerre a viagem primeiro.");
            }
            viagemRepository.deleteAllByTurmaMotoristaId(id);
        }

        try {
            if (usuario.getFirebaseUid() != null && !usuario.getFirebaseUid().isBlank()) {
                FirebaseAuth.getInstance().deleteUser(usuario.getFirebaseUid());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha na exclusão da conta no Firebase.", e);
        }

        presencaRepository.deleteAllByUsuarioId(id); 
        usuarioRepository.deleteById(id);
    }
}