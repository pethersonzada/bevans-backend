package com.bevans.model;
import com.bevans.dto.InscricaoExcursaoRequest;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="inscricoes_excursao")
public class InscricaoExcursao {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="excursao_id",nullable=false) private Excursao excursao;
    @OneToOne(optional=false) @JoinColumn(name="credencial_id",nullable=false,unique=true) private CredencialInscricaoExcursao credencial;
    @Column(nullable=false,length=120) private String nome;
    @Column(nullable=false,length=150) private String contato;
    @Column(nullable=false,length=64) private String participanteHash;
    @ManyToOne @JoinColumn(name="parada_id") private ParadaExcursao parada;
    @Column(nullable=false,length=120) private String nomePonto;
    @Column(nullable=false) private double latitude;
    @Column(nullable=false) private double longitude;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private StatusInscricaoExcursao estado;
    @Column(nullable=false) private Instant reservaValidaAte;
    @Column(nullable=false) private Instant consentimentoEm;
    private Instant confirmadaEm;
    public Long getId(){return id;}public Excursao getExcursao(){return excursao;}public String getNome(){return nome;}public String getContato(){return contato;}
    public String getParticipanteHash(){return participanteHash;}public ParadaExcursao getParada(){return parada;}
    public String getNomePonto(){return nomePonto;}public double getLatitude(){return latitude;}public double getLongitude(){return longitude;}
    public StatusInscricaoExcursao getEstado(){return estado;}public Instant getReservaValidaAte(){return reservaValidaAte;}
    public Instant getConfirmadaEm(){return confirmadaEm;}public Instant getConsentimentoEm(){return consentimentoEm;}
    public void criar(Excursao e,CredencialInscricaoExcursao cred,String nome,String contato,String hash,ParadaExcursao p,String ponto,double lat,double lon,Instant agora,Instant reserva){
        excursao=e;credencial=cred;this.nome=nome;this.contato=contato;participanteHash=hash;parada=p;nomePonto=ponto;latitude=lat;longitude=lon;
        consentimentoEm=agora;reservaValidaAte=reserva;estado=StatusInscricaoExcursao.SOLICITADA;
    }
    public void cancelar(){estado=StatusInscricaoExcursao.CANCELADA;}
    public void confirmar(Instant agora){estado=StatusInscricaoExcursao.CONFIRMADA;confirmadaEm=agora;}
}
