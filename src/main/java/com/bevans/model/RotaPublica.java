package com.bevans.model;

import jakarta.persistence.*;

/** Publicação explícita de serviço urbano; não contém passageiros nem GPS. */
@Entity
@Table(name="rotas_publicas")
public class RotaPublica {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional=false) @JoinColumn(name="turma_id",nullable=false,unique=true)
    private Turma turma;
    @Column(nullable=false,length=120)
    private String nome;
    @Column(nullable=false,length=120)
    private String cidade;
    @Enumerated(EnumType.STRING) @Column(nullable=false)
    private CategoriaRota categoria;
    @Enumerated(EnumType.STRING) @Column(nullable=false)
    private ModoRota modo;
    @Column(nullable=false)
    private String origemNome;
    @Column(nullable=false)
    private Double origemLatitude;
    @Column(nullable=false)
    private Double origemLongitude;
    @Column(nullable=false)
    private String destinoNome;
    @Column(nullable=false)
    private Double destinoLatitude;
    @Column(nullable=false)
    private Double destinoLongitude;
    @Column(length=2000)
    private String descricao;
    
    private String regiao;
    
    private String campus;
    
    private java.time.LocalTime horarioIda;
    
    private java.time.LocalTime horarioVolta;
    @Column(nullable=false)
    private boolean publicada;
    @Column(nullable=false)
    private boolean ativa;
    @Version
    private long versao;
    @ElementCollection(fetch=FetchType.EAGER)
    @org.hibernate.annotations.BatchSize(size=50)
    @CollectionTable(name="rota_publica_dias",joinColumns=@JoinColumn(name="rota_id"))
    @Column(name="dia",nullable=false) @Enumerated(EnumType.STRING)
    private java.util.Set<java.time.DayOfWeek> dias = new java.util.HashSet<>();
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma value) { turma=value; }
    public String getNome() { return nome; }
    public void setNome(String value) { nome=value; }
    public String getCidade() { return cidade; }
    public void setCidade(String value) { cidade=value; }
    public CategoriaRota getCategoria() { return categoria; }
    public void setCategoria(CategoriaRota value) { categoria=value; }
    public ModoRota getModo() { return modo; }
    public void setModo(ModoRota value) { modo=value; }
    public String getOrigemNome() { return origemNome; }
    public void setOrigemNome(String value) { origemNome=value; }
    public Double getOrigemLatitude() { return origemLatitude; }
    public void setOrigemLatitude(Double value) { origemLatitude=value; }
    public Double getOrigemLongitude() { return origemLongitude; }
    public void setOrigemLongitude(Double value) { origemLongitude=value; }
    public String getDestinoNome() { return destinoNome; }
    public void setDestinoNome(String value) { destinoNome=value; }
    public Double getDestinoLatitude() { return destinoLatitude; }
    public void setDestinoLatitude(Double value) { destinoLatitude=value; }
    public Double getDestinoLongitude() { return destinoLongitude; }
    public void setDestinoLongitude(Double value) { destinoLongitude=value; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String value) { descricao=value; }
    public String getRegiao() { return regiao; }
    public void setRegiao(String value) { regiao=value; }
    public String getCampus() { return campus; }
    public void setCampus(String value) { campus=value; }
    public java.time.LocalTime getHorarioIda() { return horarioIda; }
    public void setHorarioIda(java.time.LocalTime value) { horarioIda=value; }
    public java.time.LocalTime getHorarioVolta() { return horarioVolta; }
    public void setHorarioVolta(java.time.LocalTime value) { horarioVolta=value; }
    public boolean getPublicada() { return publicada; }
    public void setPublicada(boolean value) { publicada=value; }
    public boolean getAtiva() { return ativa; }
    public void setAtiva(boolean value) { ativa=value; }
    public long getVersao() { return versao; }
    public void setVersao(long value) { versao=value; }
    public java.util.Set<java.time.DayOfWeek> getDias() { return dias; }
    public void setDias(java.util.Set<java.time.DayOfWeek> value) { dias=value; }
}

