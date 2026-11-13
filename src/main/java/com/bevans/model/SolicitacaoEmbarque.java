package com.bevans.model;

import jakarta.persistence.*;

/** Pedido privado de embarque. Não é uma parada pública nem uma nova matrícula. */
@Entity
@Table(name="solicitacoes_embarque",indexes=@Index(name="idx_embarque_execucao",columnList="rota_id,data_operacao,sentido,status"))
public class SolicitacaoEmbarque {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="rota_id",nullable=false)
    private RotaPublica rota;
    @ManyToOne(optional=false) @JoinColumn(name="aluno_id",nullable=false)
    private Usuario aluno;
    
    private Long vinculoId;
    @ManyToOne @JoinColumn(name="parada_id")
    private ParadaPublica parada;
    @Column(nullable=false)
    private java.time.LocalDate dataOperacao;
    @Column(nullable=false,length=5)
    private String sentido;
    @Column(nullable=false,length=120)
    private String nomePonto;
    @Column(nullable=false)
    private double latitude;
    @Column(nullable=false)
    private double longitude;
    @Column(length=500)
    private String observacao;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20)
    private StatusEmbarque status;
    @Column(nullable=false)
    private java.time.Instant criadoEm;
    
    private java.time.Instant analisadoEm;
    
    private Long analisadoPor;
    @Version
    private long versao;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public RotaPublica getRota() { return rota; }
    public void setRota(RotaPublica value) { rota=value; }
    public Usuario getAluno() { return aluno; }
    public void setAluno(Usuario value) { aluno=value; }
    public Long getVinculoId() { return vinculoId; }
    public void setVinculoId(Long value) { vinculoId=value; }
    public ParadaPublica getParada() { return parada; }
    public void setParada(ParadaPublica value) { parada=value; }
    public java.time.LocalDate getDataOperacao() { return dataOperacao; }
    public void setDataOperacao(java.time.LocalDate value) { dataOperacao=value; }
    public String getSentido() { return sentido; }
    public void setSentido(String value) { sentido=value; }
    public String getNomePonto() { return nomePonto; }
    public void setNomePonto(String value) { nomePonto=value; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double value) { latitude=value; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double value) { longitude=value; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String value) { observacao=value; }
    public StatusEmbarque getStatus() { return status; }
    public void setStatus(StatusEmbarque value) { status=value; }
    public java.time.Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(java.time.Instant value) { criadoEm=value; }
    public java.time.Instant getAnalisadoEm() { return analisadoEm; }
    public void setAnalisadoEm(java.time.Instant value) { analisadoEm=value; }
    public Long getAnalisadoPor() { return analisadoPor; }
    public void setAnalisadoPor(Long value) { analisadoPor=value; }
    public long getVersao() { return versao; }
    public void setVersao(long value) { versao=value; }
}

