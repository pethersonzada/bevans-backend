package com.bevans.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class MatrizViariaMapboxTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void preservaTemposDirecionaisSemSimetrizar() throws Exception {
        long[][] m=MatrizViariaMapbox.interpretar(json.readTree("""
            {"code":"Ok","durations":[[0,12.5],[40.1,0]]}
            """),2,2);
        assertArrayEquals(new long[]{0,12500},m[0]);
        assertArrayEquals(new long[]{40100,0},m[1]);
    }
    @Test void rejeitaMatrizIncompletaOuPontoInacessivel() throws Exception {
        assertThrows(ResponseStatusException.class,()->MatrizViariaMapbox.interpretar(json.readTree("{\"code\":\"Ok\",\"durations\":[[0,null],[1,0]]}"),2,2));
        assertThrows(ResponseStatusException.class,()->MatrizViariaMapbox.interpretar(json.readTree("{\"code\":\"Ok\",\"durations\":[[0]]}"),2,2));
    }
    @Test void semTokenFalhaExplicitamente(){
        var matriz=new MatrizViariaMapbox(true,"");
        assertTrue(matriz.habilitada());
        assertThrows(ResponseStatusException.class,()->matriz.calcular(new double[]{-8,-8.1},new double[]{-35,-35.1}));
    }
    @Test void coordenadasIdenticasCompartilhamCustoSemChamadaExterna(){
        var matriz=new MatrizViariaMapbox(true,"token-de-teste");
        var m=matriz.calcular(new double[]{-8,-8,-8},new double[]{-35,-35,-35});
        assertArrayEquals(new long[]{0,0,0},m[1]);
    }
}
