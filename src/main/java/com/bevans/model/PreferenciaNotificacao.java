package com.bevans.model;
import jakarta.persistence.*;
@Entity @Table(name="preferencias_notificacao")
public class PreferenciaNotificacao {
    @Id public Long usuarioId;
    @Column(nullable=false) public boolean pushOperacional=true;
    @Column(nullable=false) public boolean pushInformativo=true;
}
