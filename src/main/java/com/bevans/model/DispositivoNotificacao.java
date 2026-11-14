package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="dispositivos_notificacao")
public class DispositivoNotificacao {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public Long usuarioId;
    @Column(nullable=false,unique=true,length=128) public String instalacaoId;
    @Column(nullable=false,length=4096) public String token;
    @Column(nullable=false,unique=true,length=64) public String tokenHash;
    @Column(nullable=false) public boolean ativo=true;
    @Column(nullable=false) public Instant atualizadoEm;
    @Override public String toString(){return "DispositivoNotificacao[segredo protegido]";}
}
