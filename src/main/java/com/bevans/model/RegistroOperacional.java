package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
/** Entidade interna. Endpoints usam DTOs e não aceitam esta entidade no body. */
@Entity @Table(name="registros_operacionais",uniqueConstraints={@UniqueConstraint(columnNames={"operacao_id","aluno_id"}),@UniqueConstraint(columnNames={"operacao_id","inscricao_id"})})
public class RegistroOperacional {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) @JoinColumn(name="operacao_id",nullable=false) public OperacaoViagem operacao;
    @ManyToOne @JoinColumn(name="aluno_id") public Usuario aluno;
    @ManyToOne @JoinColumn(name="inscricao_id") public InscricaoExcursao inscricao;
    @Column(nullable=false,length=255) public String nome;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public EstadoOperacional estado=EstadoOperacional.AGUARDANDO;
    @Column(nullable=false) public int ordem;
    public Long paradaId;
    @Column(nullable=false) public double latitude;
    @Column(nullable=false) public double longitude;
    @Column(length=80) public String autor;
    @Column(length=20) public String origem;
    public Instant atualizadoEm;
    @Version public long versao;
}
