package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="outbox_notificacao")
public class OutboxNotificacao {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,unique=true,length=200) public String chave;
    @Column(nullable=false,length=60) public String tipo;
    public Long operacaoId;public Long registroId;public Long usuarioId;public Long inscricaoId;
    @Column(length=20) public String origem;
    @Column(length=40) public String contextoTipo;
    public Long contextoId;
    @Column(nullable=false,length=20) public String estado="PENDENTE";
    @Column(nullable=false) public int tentativas;
    @Column(nullable=false) public Instant criadaEm;
    @Column(nullable=false) public Instant proximaTentativa;
}
