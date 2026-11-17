package com.bevans.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "viagem")
@SQLDelete(sql = "UPDATE viagem SET excluido = true WHERE id=?")
@SQLRestriction("excluido = false")
public class Viagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    @Column(nullable = false)
    private String sentido;

    @Column(nullable = false)
    private String status;

    @Column(name = "data_criacao", updatable = false)
    private LocalDateTime dataCriacao;
    private java.time.Instant iniciadaEm;
    // Nulo apenas nas viagens legadas anteriores à separação explícita.
    @Column(length=10) private String modalidade;
    @Column(name="rota_publica_id") private Long rotaPublicaId;
    public String getModalidade(){return modalidade;}
    public Long getRotaPublicaId(){return rotaPublicaId;}
    public void definirModalidade(String modalidade,Long rotaPublicaId){
        if(id!=null)throw new IllegalStateException("Modalidade de viagem iniciada é imutável.");
        this.modalidade=modalidade;this.rotaPublicaId=rotaPublicaId;
    }
    public java.time.Instant getIniciadaEm(){return iniciadaEm;}
    public void registrarInicio(java.time.Instant instante,java.time.ZoneId zona){
        if(id!=null)throw new IllegalStateException("Início de viagem persistida não pode ser reescrito");
        this.iniciadaEm=instante.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        this.dataCriacao=LocalDateTime.ofInstant(this.iniciadaEm,zona);
    }

    @Column(nullable = false)
    private boolean excluido = false;

    @PrePersist
    protected void onCreate() {
        if(this.dataCriacao==null)this.dataCriacao = LocalDateTime.now();
        if(this.iniciadaEm==null)this.iniciadaEm = java.time.Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public String getSentido() { return sentido; }
    public void setSentido(String sentido) { this.sentido = sentido; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
    
    public boolean isExcluido() { return excluido; }
    public void setExcluido(boolean excluido) { this.excluido = excluido; }
}
