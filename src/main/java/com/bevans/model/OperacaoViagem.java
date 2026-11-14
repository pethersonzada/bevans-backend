package com.bevans.model;
import jakarta.persistence.*;
@Entity @Table(name="operacoes_viagem",uniqueConstraints=@UniqueConstraint(columnNames={"tipo","referencia_id"}))
public class OperacaoViagem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public TipoOperacao tipo;
    @Column(nullable=false) public Long referenciaId;
    @ManyToOne @JoinColumn(name="viagem_id",unique=true) public Viagem viagem;
    @ManyToOne @JoinColumn(name="execucao_excursao_id",unique=true) public ExecucaoExcursao execucao;
    @ManyToOne(optional=false) @JoinColumn(name="motorista_id",nullable=false) public Usuario motorista;
    @Column(nullable=false,length=5) public String sentido;
    @Version public long versao;
}
