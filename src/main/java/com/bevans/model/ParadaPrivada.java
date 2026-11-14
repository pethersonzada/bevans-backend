package com.bevans.model;
import jakarta.persistence.*;
import java.time.LocalTime;
@Entity @Table(name="paradas_privadas")
public class ParadaPrivada {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="coleta_id",nullable=false) private ColetaPrivada coleta;
    @Column(nullable=false,length=120) private String nome;
    @Column(nullable=false) private double latitude;
    @Column(nullable=false) private double longitude;
    @Column(nullable=false) private int ordem;
    private LocalTime horario;
    @Column(nullable=false) private boolean ativa=true;
    public Long getId(){return id;}public ColetaPrivada getColeta(){return coleta;}public String getNome(){return nome;}
    public double getLatitude(){return latitude;}public double getLongitude(){return longitude;}public int getOrdem(){return ordem;}
    public LocalTime getHorario(){return horario;}public boolean isAtiva(){return ativa;}
    public void desativar(){ativa=false;}
    public void configurar(ColetaPrivada c,String nome,double lat,double lon,int ordem,LocalTime h){coleta=c;this.nome=nome;latitude=lat;longitude=lon;this.ordem=ordem;horario=h;ativa=true;}
}
