package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="notificacoes")
public class Notificacao {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long usuarioId;public Long inscricaoId;public Long operacaoId;public Long registroId;
    @Column(nullable=false,length=60) public String tipo;
    @Column(nullable=false,length=40) public String contextoTipo;
    @Column(nullable=false) public Long contextoId;
    @Column(nullable=false,unique=true,length=250) public String chave;
    @Column(nullable=false,length=120) public String titulo;
    @Column(nullable=false,length=255) public String mensagem;
    @Column(nullable=false) public Instant criadaEm;
    public Instant lidaEm;
}
