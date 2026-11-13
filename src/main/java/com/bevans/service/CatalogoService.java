package com.bevans.service;

import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CatalogoService {
    private final RotaPublicaRepository rotas;
    private final AlunoTurmaRepository vinculos;
    private final FavoritoRepository favoritos;
    public CatalogoService(RotaPublicaRepository rotas,AlunoTurmaRepository vinculos,FavoritoRepository favoritos){this.rotas=rotas;this.vinculos=vinculos;this.favoritos=favoritos;}
    private String filtro(String s) {
        if(s==null||s.isBlank())return null;
        if(s.length()>160)throw new IllegalArgumentException("Filtro deve ter até 160 caracteres.");return s.trim();
    }
    private Expression<String> normalizar(CriteriaBuilder b,Expression<String> e) {
        // A mesma função e o mesmo alfabeto para campo e parâmetro em H2/PostgreSQL.
        return b.function("translate",String.class,b.lower(e),b.literal("áàâãäéèêëíìîïóòôõöúùûüçñýÿ"),b.literal("aaaaaeeeeiiiiooooouuuucnyy"));
    }
    private Predicate contem(CriteriaBuilder b,Expression<String> campo,String valor) {
        String escapado=valor.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
        return b.like(normalizar(b,campo),normalizar(b,b.literal("%"+escapado+"%")),'\\');
    }
    public Specification<RotaPublica> especificacao(FiltroCatalogo f) {
        if(f==null)throw new IllegalArgumentException("Informe os filtros de consulta.");
        String q=filtro(f.q()),cidade=filtro(f.cidade()),destino=filtro(f.destino()),campus=filtro(f.campus()),regiao=filtro(f.regiao()),turno=filtro(f.turno());
        return (r,query,b)->{
            List<Predicate> p=new ArrayList<>();
            p.add(b.isTrue(r.get("publicada")));p.add(b.isTrue(r.get("ativa")));p.add(b.isFalse(r.get("turma").get("excluido")));
            p.add(b.isNotNull(r.get("turma").get("capacidade")));
            if(cidade!=null)p.add(b.equal(normalizar(b,r.get("cidade")),normalizar(b,b.literal(cidade))));
            if(destino!=null)p.add(contem(b,r.get("destinoNome"),destino));
            if(campus!=null)p.add(contem(b,r.get("campus"),campus));
            if(regiao!=null)p.add(contem(b,r.get("regiao"),regiao));
            if(turno!=null)p.add(b.equal(normalizar(b,r.get("turma").get("turno")),normalizar(b,b.literal(turno))));
            if(f.categoria()!=null)p.add(b.equal(r.get("categoria"),f.categoria()));
            if(f.dia()!=null)p.add(b.isMember(f.dia(),r.get("dias")));
            if(q!=null)p.add(b.or(contem(b,r.get("nome"),q),contem(b,r.get("destinoNome"),q),contem(b,r.get("campus"),q),contem(b,r.get("regiao"),q)));
            if(f.somenteComVagas()) {
                Subquery<Long> ocupados=query.subquery(Long.class);Root<AlunoTurma> v=ocupados.from(AlunoTurma.class);
                ocupados.select(b.count(v)).where(b.equal(v.get("turma").get("id"),r.get("turma").get("id")),b.equal(v.get("status"),"APROVADO"),b.isFalse(v.get("excluido")));
                p.add(b.lt(ocupados,r.get("turma").get("capacidade")));
            }
            return b.and(p.toArray(Predicate[]::new));
        };
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PaginaDTO<CatalogoRotaDTO> buscar(FiltroCatalogo f,int pagina,int tamanho) {
        return buscar(f,pagina,tamanho,null,false);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PaginaDTO<CatalogoRotaDTO> buscar(FiltroCatalogo f,int pagina,int tamanho,Long usuarioId,boolean somenteFavoritas) {
        if(pagina<0||pagina>10000||tamanho<1||tamanho>50)throw new IllegalArgumentException("Página deve ser 0..10000 e tamanho 1..50.");
        Specification<RotaPublica> spec=especificacao(f);
        if(somenteFavoritas) {
            if(usuarioId==null)throw new org.springframework.security.access.AccessDeniedException("Sessão inválida.");
            spec=spec.and((r,query,b)->{
                Subquery<Long> sub=query.subquery(Long.class);Root<FavoritoRota> favorito=sub.from(FavoritoRota.class);
                sub.select(favorito.get("id")).where(b.equal(favorito.get("usuario").get("id"),usuarioId),b.equal(favorito.get("rota").get("id"),r.get("id")),b.isTrue(favorito.get("ativo")));
                return b.exists(sub);
            });
        }
        Page<RotaPublica> page=rotas.findAll(spec,PageRequest.of(pagina,tamanho,Sort.by(Sort.Direction.ASC,"id")));
        List<Long> ids=page.getContent().stream().map(r->r.getTurma().getId()).toList();
        Map<Long,Long> ocupacao=ids.isEmpty()?Map.of():vinculos.ocupacaoDasTurmas(ids).stream().collect(Collectors.toMap(AlunoTurmaRepository.OcupacaoTurma::getTurmaId,AlunoTurmaRepository.OcupacaoTurma::getOcupados));
        Set<Long> favoritas=usuarioId==null||page.isEmpty()?Set.of():favoritos.findByUsuarioIdAndAtivoTrueAndRotaIdIn(usuarioId,page.getContent().stream().map(RotaPublica::getId).toList())
                .stream().map(fav->fav.getRota().getId()).collect(Collectors.toSet());
        List<CatalogoRotaDTO> itens=page.getContent().stream().map(r->{
            Turma t=r.getTurma();long usados=ocupacao.getOrDefault(t.getId(),0L);Integer cap=t.getCapacidade();
            return new CatalogoRotaDTO(r.getId(),t.getId(),r.getNome(),t.getMotorista()==null?null:t.getMotorista().getNome(),r.getCidade(),r.getCategoria(),r.getModo(),
                    r.getDestinoNome(),r.getCampus(),r.getRegiao(),t.getTurno(),r.getDias().stream().sorted().toList(),r.getHorarioIda(),r.getHorarioVolta(),
                    new VagasTurmaDTO(cap,usados,cap==null?null:Math.max(0L,cap.longValue()-usados),cap!=null),favoritas.contains(r.getId()));
        }).toList();
        return new PaginaDTO<>(itens,page.getTotalElements(),page.getNumber(),page.getSize(),page.getTotalPages());
    }
}
