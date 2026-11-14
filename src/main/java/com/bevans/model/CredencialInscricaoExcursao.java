package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="credenciais_inscricao_excursao")
public class CredencialInscricaoExcursao {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="excursao_id",nullable=false) private Excursao excursao;
    @Column(nullable=false,unique=true,length=64) private String tokenHash;
    @Column(nullable=false) private Instant expiraEm;
    public Long getId(){return id;}public Excursao getExcursao(){return excursao;}public Instant getExpiraEm(){return expiraEm;}
    public void criar(Excursao e,String hash,Instant expira){excursao=e;tokenHash=hash;expiraEm=expira;}
    public void prolongar(Instant expira){expiraEm=expira;}
}
