package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
/** Outbox sem FK para a conta removida; somente identificador necessário ao provedor. */
@Entity @Table(name="exclusoes_conta")
public class ExclusaoConta {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,unique=true,length=128) public String firebaseUid;
    @Column(nullable=false,length=20) public String estado="PENDENTE";
    @Column(nullable=false) public int tentativas;
    @Column(nullable=false) public Instant solicitadaEm;
    @Column(nullable=false) public Instant proximaTentativa;
    public Instant concluidaEm;
    @Override public String toString(){return "ExclusaoConta[identidade protegida]";}
}
