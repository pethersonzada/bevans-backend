package com.bevans.model;
import jakarta.persistence.*;
@Entity @Table(name="coletas_privadas",uniqueConstraints=@UniqueConstraint(columnNames={"turma_id","sentido"}))
public class ColetaPrivada {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="turma_id",nullable=false) private Turma turma;
    @Column(nullable=false,length=5) private String sentido;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ModoColetaPrivada modo;
    @Column(nullable=false) private long revisao;
    @Version private long versao;
    public Long getId(){return id;}public Turma getTurma(){return turma;}public String getSentido(){return sentido;}
    public ModoColetaPrivada getModo(){return modo;}public long getRevisao(){return revisao;}
    public void criar(Turma t,String s){turma=t;sentido=s;modo=ModoColetaPrivada.PORTA_A_PORTA;}
    public void alterar(ModoColetaPrivada m){modo=m;revisao++;}
}
