package com.bevans.security;
import com.bevans.repository.RotaPublicaRepository;
import org.springframework.stereotype.Component;
@Component("rotasPublicasAccess")
public class RotaPublicaAccess {
    private final RotaPublicaRepository rotas;
    private final AccessPolicy access;
    public RotaPublicaAccess(RotaPublicaRepository rotas, AccessPolicy access) {this.rotas=rotas;this.access=access;}
    public boolean own(Long id) {
        return id!=null && rotas.findById(id).map(r->r.getTurma()!=null && access.driverOfTurma(r.getTurma().getId())).orElse(false);
    }
    public boolean canRead(Long id) {
        return id!=null && rotas.findById(id).map(r->r.getTurma()!=null && !r.getTurma().isExcluido()
                && ((r.getPublicada() && r.getAtiva()) || access.driverOfTurma(r.getTurma().getId()))).orElse(false);
    }
}
