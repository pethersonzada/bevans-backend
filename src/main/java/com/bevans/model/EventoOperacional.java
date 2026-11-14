package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="eventos_operacionais",uniqueConstraints=@UniqueConstraint(columnNames={"registro_id","versao_registro"}))
public class EventoOperacional {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) @JoinColumn(name="registro_id",nullable=false) public RegistroOperacional registro;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public EstadoOperacional anterior;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public EstadoOperacional novo;
    @Column(nullable=false,length=80) public String autor;
    @Column(nullable=false,length=20) public String origem;
    @Column(nullable=false) public Instant criadoEm;
    @Column(nullable=false) public boolean correcao;
    @Column(length=500) public String motivo;
    @Column(nullable=false) public long versaoRegistro;
}
