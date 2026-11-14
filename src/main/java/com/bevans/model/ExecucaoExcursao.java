package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="execucoes_excursao",uniqueConstraints=@UniqueConstraint(columnNames={"excursao_id","sentido"}))
public class ExecucaoExcursao {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="excursao_id",nullable=false) private Excursao excursao;
    @Column(nullable=false,length=5) private String sentido;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EstadoExecucaoExcursao estado;
    @Column(nullable=false) private Instant iniciadaEm;
    private Instant finalizadaEm;
    @Column(nullable=false,columnDefinition="text",updatable=false) private String roteiroJson;
    @Column(nullable=false,updatable=false) private int snapshotVersao=1;
    @Version private long versao;
    public Long getId(){return id;}public Excursao getExcursao(){return excursao;}public String getSentido(){return sentido;}
    public EstadoExecucaoExcursao getEstado(){return estado;}public Instant getIniciadaEm(){return iniciadaEm;}public Instant getFinalizadaEm(){return finalizadaEm;}
    public String getRoteiroJson(){return roteiroJson;}public int getSnapshotVersao(){return snapshotVersao;}public long getVersao(){return versao;}
    public void criar(Excursao e,String sentido,String roteiro,Instant agora){excursao=e;this.sentido=sentido;roteiroJson=roteiro;iniciadaEm=agora;estado=EstadoExecucaoExcursao.EM_ANDAMENTO;}
    public void finalizar(Instant agora){estado=EstadoExecucaoExcursao.FINALIZADA;finalizadaEm=agora;}
}
