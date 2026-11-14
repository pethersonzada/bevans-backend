package com.bevans.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="entregas_push",uniqueConstraints=@UniqueConstraint(columnNames={"notificacao_id","dispositivo_id"}))
public class EntregaPush {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false) @JoinColumn(name="notificacao_id",nullable=false) public Notificacao notificacao;
    @ManyToOne(optional=false) @JoinColumn(name="dispositivo_id",nullable=false) public DispositivoNotificacao dispositivo;
    @Column(nullable=false,length=20) public String estado="PENDENTE";
    @Column(nullable=false) public int tentativas;
    @Column(nullable=false) public Instant proximaTentativa;
    @Column(length=40) public String resultado;
}
