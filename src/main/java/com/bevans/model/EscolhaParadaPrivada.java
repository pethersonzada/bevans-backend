package com.bevans.model;
import jakarta.persistence.*;
@Entity @Table(name="escolhas_parada_privada",uniqueConstraints=@UniqueConstraint(columnNames={"coleta_id","aluno_id"}))
public class EscolhaParadaPrivada {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="coleta_id",nullable=false) private ColetaPrivada coleta;
    @ManyToOne(optional=false) @JoinColumn(name="aluno_id",nullable=false) private Usuario aluno;
    @ManyToOne(optional=false) @JoinColumn(name="parada_id",nullable=false) private ParadaPrivada parada;
    @Column(nullable=false) private boolean ativa=true;
    public Long getId(){return id;}public Usuario getAluno(){return aluno;}public ParadaPrivada getParada(){return parada;}public boolean isAtiva(){return ativa;}
    public void escolher(ColetaPrivada c,Usuario u,ParadaPrivada p){coleta=c;aluno=u;parada=p;ativa=true;}
    public void remover(){ativa=false;}
}
