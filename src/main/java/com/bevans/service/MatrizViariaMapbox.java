package com.bevans.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Matriz direcional de tempos de condução. Envia somente coordenadas, nunca nomes ou IDs. */
@Service
public class MatrizViariaMapbox {
    private final boolean enabled;
    private final String token;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json=new ObjectMapper();

    public MatrizViariaMapbox(@Value("${APP_MAPBOX_MATRIX_ENABLED:false}") boolean enabled,
                             @Value("${APP_MAPBOX_MATRIX_TOKEN:}") String token){
        this.enabled=enabled;this.token=token.trim();
    }
    public boolean habilitada(){return enabled;}

    public long[][] calcular(double[] lats,double[] lons){
        if(token.isEmpty())throw indisponivel("Configure a matriz viária no servidor.");
        if(lats.length!=lons.length||lats.length<2)throw new IllegalArgumentException("Pontos inválidos.");
        // Coordenadas idênticas (ex.: vários moradores do mesmo residencial) compartilham custo.
        var unicos=new ArrayList<String>();var indices=new LinkedHashMap<String,Integer>();int[] mapa=new int[lats.length];
        for(int i=0;i<lats.length;i++){
            if(!Double.isFinite(lats[i])||!Double.isFinite(lons[i])||Math.abs(lats[i])>90||Math.abs(lons[i])>180)
                throw new IllegalArgumentException("Coordenadas inválidas.");
            String ponto=lons[i]+","+lats[i];
            if(!indices.containsKey(ponto)){indices.put(ponto,unicos.size());unicos.add(ponto);}
            mapa[i]=indices.get(ponto);
        }
        int n=unicos.size();long[][] custo=new long[n][n];
        // Um pedido para até 25 pontos; acima disso, blocos de até 12 origens
        // e 12 destinos respeitam o limite de 25 coordenadas por requisição.
        long prazo=System.nanoTime()+Duration.ofSeconds(12).toNanos();
        if(n>1){
            int bloco=n<=25?n:12;
            for(int i=0;i<n;i+=bloco)for(int j=0;j<n;j+=bloco){
                var origens=unicos.subList(i,Math.min(i+bloco,n));
                var destinos=unicos.subList(j,Math.min(j+bloco,n));
                var pontos=new ArrayList<>(new LinkedHashSet<>(origens));for(var p:destinos)if(!pontos.contains(p))pontos.add(p);
                if(pontos.size()==1)continue;
                String src=origens.stream().map(p->String.valueOf(pontos.indexOf(p))).collect(Collectors.joining(";"));
                String dst=destinos.stream().map(p->String.valueOf(pontos.indexOf(p))).collect(Collectors.joining(";"));
                String endereco="https://api.mapbox.com/directions-matrix/v1/mapbox/driving/"+String.join(";",pontos)
                    +"?annotations=duration&sources="+src+"&destinations="+dst+"&access_token="+URLEncoder.encode(token,StandardCharsets.UTF_8);
                long restante=prazo-System.nanoTime();
                if(restante<=0)throw indisponivel("Tempo limite ao calcular a rota pelas ruas.");
                try{
                    var req=HttpRequest.newBuilder(URI.create(endereco)).timeout(Duration.ofNanos(restante)).GET().build();
                    var resp=client.send(req,HttpResponse.BodyHandlers.ofString());
                    if(resp.statusCode()!=200)throw indisponivel("Serviço de matriz viária indisponível (HTTP "+resp.statusCode()+").");
                    var parte=interpretar(json.readTree(resp.body()),origens.size(),destinos.size());
                    for(int a=0;a<origens.size();a++)for(int b=0;b<destinos.size();b++)custo[i+a][j+b]=parte[a][b];
                }catch(InterruptedException e){Thread.currentThread().interrupt();throw indisponivel("Cálculo de rota interrompido.");}
                catch(java.io.IOException|IllegalArgumentException e){throw indisponivel("Não foi possível consultar a matriz viária.");}
            }
        }
        long[][] resultado=new long[lats.length][lats.length];
        for(int i=0;i<mapa.length;i++)for(int j=0;j<mapa.length;j++)resultado[i][j]=custo[mapa[i]][mapa[j]];
        return resultado;
    }

    static long[][] interpretar(JsonNode resposta,int linhas,int colunas){
        if(resposta==null||!"Ok".equals(resposta.path("code").asText()))throw indisponivel("Matriz viária sem rota válida.");
        JsonNode dados=resposta.path("durations");
        if(!dados.isArray()||dados.size()!=linhas)throw indisponivel("Matriz viária incompleta.");
        long[][] custo=new long[linhas][colunas];
        for(int i=0;i<linhas;i++){
            JsonNode linha=dados.get(i);
            if(!linha.isArray()||linha.size()!=colunas)throw indisponivel("Matriz viária incompleta.");
            for(int j=0;j<colunas;j++){
                var v=linha.get(j);
                if(!v.isNumber()||!Double.isFinite(v.asDouble())||v.asDouble()<0||v.asDouble()>1e8)
                    throw indisponivel("Há pontos sem conexão viária válida. Revise os endereços.");
                custo[i][j]=Math.round(v.asDouble()*1000);
            }
        }
        return custo;
    }
    private static ResponseStatusException indisponivel(String msg){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,msg);}
}
