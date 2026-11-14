package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="contatos_motorista")
public class ContatoMotorista {
    @Id public Long motoristaId;
    @Column(length=15) public String telefoneComercial;
    @Column(nullable=false) public boolean habilitado;
    public Instant consentimentoEm;
    @Column(nullable=false) public Instant atualizadoEm;
    @Version public long versao;
    @Override public String toString(){return "ContatoMotorista[dados protegidos]";}
}
