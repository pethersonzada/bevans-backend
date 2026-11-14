package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="convites_responsavel")
public class ConviteResponsavel {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) @JoinColumn(name="passageiro_id",nullable=false) public Usuario passageiro;
    @Column(nullable=false,unique=true,length=64) public String tokenHash;
    @Column(nullable=false) public Instant expiraEm;
    public Instant consumidoEm,revogadoEm;
    @Override public String toString(){return "ConviteResponsavel[segredo protegido]";}
}
