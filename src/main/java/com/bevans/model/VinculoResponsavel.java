package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="vinculos_responsavel",uniqueConstraints=@UniqueConstraint(columnNames={"passageiro_id","responsavel_id"}))
public class VinculoResponsavel {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) @JoinColumn(name="passageiro_id",nullable=false) public Usuario passageiro;
    @ManyToOne(optional=false) @JoinColumn(name="responsavel_id",nullable=false) public Usuario responsavel;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) public EstadoResponsabilidade estado;
    @Column(nullable=false) public Instant criadoEm;
    public Instant consentimentoPassageiroEm,consentimentoResponsavelEm,verificadoEm,revogadoEm;
    @Column(length=40) public String termoPassageiro,termoResponsavel;
    @Column(length=200) public String referenciaVerificacao;
    @Version public long versao;
}
