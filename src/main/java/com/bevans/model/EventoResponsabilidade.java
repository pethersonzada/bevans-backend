package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="eventos_responsabilidade",uniqueConstraints=@UniqueConstraint(columnNames={"vinculo_id","versao_vinculo"}))
public class EventoResponsabilidade {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) @JoinColumn(name="vinculo_id",nullable=false) public VinculoResponsavel vinculo;
    @ManyToOne(optional=false) @JoinColumn(name="autor_id",nullable=false) public Usuario autor;
    @Column(nullable=false,length=40) public String acao;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) public EstadoResponsabilidade estado;
    @Column(nullable=false) public Instant criadoEm;
    @Column(nullable=false) public long versaoVinculo;
    @Column(length=40) public String termoVersao;
    @Column(length=200) public String referenciaVerificacao;
}
