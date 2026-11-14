package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="convites_excursao")
public class ConviteExcursao {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="excursao_id",nullable=false) private Excursao excursao;
    @Column(nullable=false,unique=true,length=64) private String tokenHash;
    @Column(nullable=false) private Instant expiraEm;
    @Column(nullable=false) private Instant criadoEm;
    private Instant revogadoEm;
    public Long getId(){return id;}
    public Excursao getExcursao(){return excursao;}
    public Instant getExpiraEm(){return expiraEm;}
    public Instant getCriadoEm(){return criadoEm;}
    public Instant getRevogadoEm(){return revogadoEm;}
    public String getTokenHash(){return tokenHash;}
    public void criar(Excursao e,String hash,Instant expira,Instant agora){excursao=e;tokenHash=hash;expiraEm=expira;criadoEm=agora;}
    public void revogar(Instant agora){if(revogadoEm==null)revogadoEm=agora;}
}
