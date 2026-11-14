package com.bevans.service;
public interface ExclusaoIdentidadeGateway {
    enum Resultado { EXCLUIDA,TRANSITORIO,PERMANENTE,DESATIVADO }
    Resultado excluir(String uid);
}
