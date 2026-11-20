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
    @org.springframework.beans.factory.annotation.Autowired(required=false) private ExclusaoContaService exclusaoContas;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.bevans.repository.ExclusaoContaRepository exclusoes;

    private final UsuarioRepository usuarioRepository;
    private final PresencaRepository presencaRepository;
    private final ViagemRepository viagemRepository;

    public UsuarioService(UsuarioRepository usuarioRepository, PresencaRepository presencaRepository, ViagemRepository viagemRepository) {
        this.usuarioRepository = usuarioRepository;
        this.presencaRepository = presencaRepository;
        this.viagemRepository = viagemRepository;
    }

    @Transactional public Usuario cadastrarUsuario(Usuario usuario) {
        if (usuarioRepository.findByEmail(usuario.getEmail()).isPresent()) {
            throw new ConflitoException("E-mail já cadastrado");
        }
        if (usuarioRepository.findByTelefone(usuario.getTelefone()).isPresent()) {
            throw new ConflitoException("Telefone já cadastrado");
        }
        
        if(usuario.getFirebaseUid()!=null&&(usuarioRepository.findByFirebaseUid(usuario.getFirebaseUid()).isPresent()||exclusoes!=null&&exclusoes.existsByFirebaseUid(usuario.getFirebaseUid())))throw new ConflitoException("Identidade Firebase indisponível ou já cadastrada");
        try{return usuarioRepository.saveAndFlush(usuario);}catch(org.springframework.dao.DataIntegrityViolationException ex){throw new ConflitoException("Identidade, email ou telefone já cadastrados");}
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

        String email = novoEmail.trim();

        // Serializa alterações da mesma conta e deixa a sincronização repetida
        // sem gravação quando o perfil já corresponde ao Firebase.
        Usuario usuario = usuarioRepository.lockById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado no sistema."));
        if (usuario.getEmail() != null && usuario.getEmail().equalsIgnoreCase(email)) {
            return usuario;
        }
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new ConflitoException("Este e-mail já está em uso por outro usuário.");
        }

        usuario.setEmail(email);
        try {
            return usuarioRepository.saveAndFlush(usuario);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ConflitoException("Este e-mail já está em uso por outro usuário.");
        }
    }

    @Transactional
    public void excluirUsuario(Long id) {
        if (id == null) throw new IllegalArgumentException("O ID fornecido para exclusão não pode ser nulo.");

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado no sistema."));
        
        if(exclusaoContas==null)throw new ConflitoException("Fluxo de exclusão indisponível");
        exclusaoContas.solicitar(usuario.getId());
    }
}
