package com.bevans.dto;
import java.util.List;
public record PaginaDTO<T>(List<T> itens,long total,int pagina,int tamanho,int totalPaginas) {}
