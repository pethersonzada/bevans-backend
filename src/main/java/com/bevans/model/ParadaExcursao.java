package com.bevans.model;
import jakarta.persistence.*;
import java.time.LocalTime;
@Entity @Table(name="paradas_excursao")
public class ParadaExcursao {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="excursao_id",nullable=false) private Excursao excursao;
    @Column(nullable=false) private int ordem;
    @Column(nullable=false,length=120) private String nome;
    @Column(nullable=false) private double latitude;
    @Column(nullable=false) private double longitude;
    private LocalTime horario;
    @Column(length=500) private String instrucoes;
    @Column(nullable=false) private boolean ativa=true;
    public Long getId(){return id;}
    public Excursao getExcursao(){return excursao;}
    public int getOrdem(){return ordem;}
    public String getNome(){return nome;}
    public double getLatitude(){return latitude;}
    public double getLongitude(){return longitude;}
    public LocalTime getHorario(){return horario;}
    public String getInstrucoes(){return instrucoes;}
    public boolean isAtiva(){return ativa;}
    public void desativar(){ativa=false;}
    public void configurar(Excursao e,int ordem,String nome,double lat,double lon,LocalTime horario,String instrucoes){
        this.excursao=e;this.ordem=ordem;this.nome=nome;latitude=lat;longitude=lon;this.horario=horario;this.instrucoes=instrucoes;ativa=true;
    }
}
