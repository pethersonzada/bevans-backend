package com.bevans.service;

import com.bevans.dto.PontoPublicoDTO;
import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.*;
import com.google.protobuf.Duration;
import java.util.*;

/** Usa o mesmo OR-Tools do transporte privado, com início e fim explícitos. */
public final class OtimizadorParadas {
    private OtimizadorParadas() {}
    public static List<PontoPublicoDTO> ordenar(List<PontoPublicoDTO> pontos) {
        if(pontos.size()<3)return List.copyOf(pontos);
        Loader.loadNativeLibraries();
        RoutingIndexManager manager=new RoutingIndexManager(pontos.size(),1,new int[]{0},new int[]{pontos.size()-1});
        RoutingModel model=new RoutingModel(manager);
        try {
            int custo=model.registerTransitCallback((a,b)->distancia(pontos.get(manager.indexToNode(a)),pontos.get(manager.indexToNode(b))));
            model.setArcCostEvaluatorOfAllVehicles(custo);
            RoutingSearchParameters parametros=main.defaultRoutingSearchParameters().toBuilder()
                    .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                    .setTimeLimit(Duration.newBuilder().setSeconds(2).build()).build();
            Assignment resultado=model.solveWithParameters(parametros);
            if(resultado==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Não foi possível otimizar as paradas. Preserve a ordem e tente novamente.");
            try {
                List<PontoPublicoDTO> ordenados=new ArrayList<>();long i=model.start(0);
                while(!model.isEnd(i)){ordenados.add(pontos.get(manager.indexToNode(i)));i=resultado.value(model.nextVar(i));}
                ordenados.add(pontos.get(manager.indexToNode(i)));return ordenados;
            } finally {resultado.delete();}
        } finally {model.delete();manager.delete();}
    }
    private static long distancia(PontoPublicoDTO a,PontoPublicoDTO b) {
        double lat=Math.toRadians(b.latitude()-a.latitude()),lon=Math.toRadians(b.longitude()-a.longitude());
        double h=Math.pow(Math.sin(lat/2),2)+Math.cos(Math.toRadians(a.latitude()))*Math.cos(Math.toRadians(b.latitude()))*Math.pow(Math.sin(lon/2),2);
        return Math.round(6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h))));
    }
}
