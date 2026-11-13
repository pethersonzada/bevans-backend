package com.bevans.model;
import com.bevans.dto.ExcursaoRequest;
import jakarta.persistence.*;
import java.time.Instant;

/** Viagem ocasional independente de Turma, RotaPublica e seus convites. */
@Entity @Table(name="excursoes")
public class Excursao {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="organizador_id",nullable=false) private Usuario organizador;
    @Column(nullable=false,length=120) private String titulo;
    @Column(nullable=false) private String destinoNome;
    @Column(nullable=false) private Double destinoLatitude;
    @Column(nullable=false) private Double destinoLongitude;
    private String origemNome;
    private Double origemLatitude;
    private Double origemLongitude;
    @Column(nullable=false) private Instant partidaEm;
    private Instant retornoEm;
    @Column(nullable=false) private int capacidade;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private EstadoExcursao estado=EstadoExcursao.RASCUNHO;
    @Version private long versao;
    @Column(nullable=false,updatable=false) private Instant criadaEm;
    public Long getId(){return id;}
    public Usuario getOrganizador(){return organizador;}
    public void setOrganizador(Usuario u){organizador=u;}
    public String getTitulo(){return titulo;}
    public String getDestinoNome(){return destinoNome;}
    public Double getDestinoLatitude(){return destinoLatitude;}
    public Double getDestinoLongitude(){return destinoLongitude;}
    public String getOrigemNome(){return origemNome;}
    public Double getOrigemLatitude(){return origemLatitude;}
    public Double getOrigemLongitude(){return origemLongitude;}
    public Instant getPartidaEm(){return partidaEm;}
    public Instant getRetornoEm(){return retornoEm;}
    public int getCapacidade(){return capacidade;}
    public EstadoExcursao getEstado(){return estado;}
    public void setEstado(EstadoExcursao e){estado=e;}
    public long getVersao(){return versao;}
    public Instant getCriadaEm(){return criadaEm;}
    public void setCriadaEm(Instant e){criadaEm=e;}
    public void configurar(ExcursaoRequest d){
        titulo=d.titulo().trim();destinoNome=d.destinoNome().trim();destinoLatitude=d.destinoLatitude();destinoLongitude=d.destinoLongitude();
        origemNome=d.origemNome()==null?null:d.origemNome().trim();origemLatitude=d.origemLatitude();origemLongitude=d.origemLongitude();
        partidaEm=d.partidaEm();retornoEm=d.retornoEm();capacidade=d.capacidade();
    }
}
