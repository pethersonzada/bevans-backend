package com.bevans.model;

import jakarta.persistence.*;

@Entity
@Table(name = "enderecos")
public class Endereco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @io.swagger.v3.oas.annotations.media.Schema(accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
    private Long id;

    private String apelido;
    private String rua;
    private String numero;
    private String bairro;
    private String complemento;
    @io.swagger.v3.oas.annotations.media.Schema(minimum="-90",maximum="90",description="Pin explicitamente confirmado; par 0,0 inválido; geocodificação aproximada não confirma residência")
    private Double latitude;
    @io.swagger.v3.oas.annotations.media.Schema(minimum="-180",maximum="180")
    private Double longitude;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    @io.swagger.v3.oas.annotations.media.Schema(accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY,description="Fixado pelo servidor ao titular autorizado do caminho")
    private Usuario usuario;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getApelido() { return apelido; }
    public void setApelido(String apelido) { this.apelido = apelido; }

    public String getRua() { return rua; }
    public void setRua(String rua) { this.rua = rua; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }

    public String getComplemento() { return complemento; }
    public void setComplemento(String complemento) { this.complemento = complemento; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
}
