package com.bevans.model;
import jakarta.persistence.*;
@Entity
@Table(name="rotas_favoritas",uniqueConstraints=@UniqueConstraint(name="uq_favorito_usuario_rota",columnNames={"usuario_id","rota_id"}))
public class FavoritoRota {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="usuario_id",nullable=false)
    private Usuario usuario;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="rota_id",nullable=false)
    private RotaPublica rota;
    @Column(nullable=false)
    private boolean ativo;
    public Long getId(){return id;}
    public Usuario getUsuario(){return usuario;}
    public void setUsuario(Usuario usuario){this.usuario=usuario;}
    public RotaPublica getRota(){return rota;}
    public void setRota(RotaPublica rota){this.rota=rota;}
    public boolean isAtivo(){return ativo;}
    public void setAtivo(boolean ativo){this.ativo=ativo;}
}
