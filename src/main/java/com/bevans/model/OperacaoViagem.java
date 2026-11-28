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
    @Column(columnDefinition="text",updatable=false) private String roteiroJson;
    @Column(length=10) private String conclusaoModo;
    @Column(length=500) private String conclusaoMotivo;
    @Column(length=80) private String conclusaoAutor;
    private java.time.Instant conclusaoEm;
    @Column(length=30) private String conclusaoGpsEstado;
    private Double conclusaoLatitude;
    private Double conclusaoLongitude;
    private Double conclusaoPrecisao;
    private java.time.Instant conclusaoGpsObservadoEm;
    private Double conclusaoDistanciaMetros;
    public void registrarConclusaoIda(String modo,String motivo,String autor,java.time.Instant em,String gpsEstado,
            Double latitude,Double longitude,Double precisao,java.time.Instant observadoEm,Double distanciaMetros){
        if(conclusaoEm!=null)throw new IllegalStateException("Conclusão já registrada.");
        conclusaoModo=modo;conclusaoMotivo=motivo;conclusaoAutor=autor;conclusaoEm=em;
        conclusaoGpsEstado=gpsEstado;conclusaoLatitude=latitude;conclusaoLongitude=longitude;
        conclusaoPrecisao=precisao;conclusaoGpsObservadoEm=observadoEm;conclusaoDistanciaMetros=distanciaMetros;
    }
    public String getConclusaoModo(){return conclusaoModo;}
    public String getConclusaoMotivo(){return conclusaoMotivo;}
    public String getConclusaoAutor(){return conclusaoAutor;}
    public java.time.Instant getConclusaoEm(){return conclusaoEm;}
    public String getConclusaoGpsEstado(){return conclusaoGpsEstado;}
    public Double getConclusaoLatitude(){return conclusaoLatitude;}
    public Double getConclusaoLongitude(){return conclusaoLongitude;}
    public Double getConclusaoDistanciaMetros(){return conclusaoDistanciaMetros;}
    public String getRoteiroJson(){return roteiroJson;}
    public void salvarRoteiro(String json){if(roteiroJson!=null)throw new IllegalStateException("Roteiro já registrado.");roteiroJson=json;}
}
