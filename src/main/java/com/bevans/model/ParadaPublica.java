package com.bevans.model;

import jakarta.persistence.*;

@Entity
@Table(name="paradas_publicas",indexes=@Index(name="idx_paradas_rota_ordem",columnList="rota_id,ordem"))
public class ParadaPublica {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="rota_id",nullable=false)
    private RotaPublica rota;
    @Column(nullable=false)
    private int ordem;
    @Column(nullable=false,length=120)
    private String nome;
    @Column(nullable=false)
    private double latitude;
    @Column(nullable=false)
    private double longitude;
    
    private java.time.LocalTime horario;
    @Column(nullable=false)
    private boolean ativa;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public RotaPublica getRota() { return rota; }
    public void setRota(RotaPublica value) { rota=value; }
    public int getOrdem() { return ordem; }
    public void setOrdem(int value) { ordem=value; }
    public String getNome() { return nome; }
    public void setNome(String value) { nome=value; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double value) { latitude=value; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double value) { longitude=value; }
    public java.time.LocalTime getHorario() { return horario; }
    public void setHorario(java.time.LocalTime value) { horario=value; }
    public boolean getAtiva() { return ativa; }
    public void setAtiva(boolean value) { ativa=value; }
}

