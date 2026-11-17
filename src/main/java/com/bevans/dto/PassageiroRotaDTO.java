package com.bevans.dto;

public class PassageiroRotaDTO {
    private Long id;
    private String nome;
    private Double latitude;
    private Double longitude;

    @com.fasterxml.jackson.annotation.JsonCreator
    public PassageiroRotaDTO(@com.fasterxml.jackson.annotation.JsonProperty("id") Long id,
            @com.fasterxml.jackson.annotation.JsonProperty("nome") String nome,
            @com.fasterxml.jackson.annotation.JsonProperty("latitude") Double latitude,
            @com.fasterxml.jackson.annotation.JsonProperty("longitude") Double longitude) {
        this.id = id;
        this.nome = nome;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
}
