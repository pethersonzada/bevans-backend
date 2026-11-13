package com.bevans.repository;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.sql.init.mode=never"})
@Import({FavoritoService.class,CatalogoService.class,RotaPublicaService.class,TurmaService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class FavoritoIntegrationTest {
    @Autowired FavoritoService service;
    @Autowired CatalogoService catalogo;
    @Autowired RotaPublicaService rotas;
    @Autowired TurmaService turmas;
    @Autowired UsuarioRepository usuarios;
    @Autowired FavoritoRepository favoritos;
    @MockBean GeocodingService geocoding;
    private record RotaDono(Long id,Long dono) {}
    private Usuario usuario(String tipo) {
        String chave=UUID.randomUUID().toString();Usuario u=new Usuario();u.setNome(chave);u.setEmail(chave+"@example.test");u.setTelefone(chave);u.setTipo(tipo);return usuarios.save(u);
    }
    private RotaDono rota() {
        Usuario dono=usuario("MOTORISTA");Turma t=new Turma();t.setNome("Turma");t.setTurno("NOITE");t.setCapacidade(12);t.setMotorista(dono);t=turmas.criarTurma(t);
        var d=new RotaPublicaRequest(t.getId(),"Rota "+t.getId(),"Caruaru",CategoriaRota.OUTROS,ModoRota.FIXA,"Terminal",-8.2,-35.9,"Campus",-8.3,-36.0,null,null,null,Set.of(DayOfWeek.MONDAY),LocalTime.of(7,0),null);
        var r=rotas.criar(dono.getId(),d);rotas.publicar(r.id(),dono.getId(),true);return new RotaDono(r.id(),dono.getId());
    }
    private FiltroCatalogo filtro(){return new FiltroCatalogo(null,null,null,null,null,null,null,null,false);}
    @Test void favoritoIdempotentePersisteEmNovasConsultasERemoveLogicamente() {
        var r=rota();Long aluno=usuario("PASSAGEIRO").getId();service.favoritar(aluno,r.id());service.favoritar(aluno,r.id());
        assertEquals(1,favoritos.countByUsuarioIdAndRotaId(aluno,r.id()));assertTrue(service.favorita(aluno,r.id()));
        assertEquals(1,service.listar(aluno,0,20).total());assertTrue(service.listar(aluno,0,20).itens().get(0).favorita());
        service.remover(aluno,r.id());service.remover(aluno,r.id());assertFalse(service.favorita(aluno,r.id()));assertEquals(0,service.listar(aluno,0,20).total());
        assertEquals(1,favoritos.countByUsuarioIdAndRotaId(aluno,r.id()));service.favoritar(aluno,r.id());assertEquals(1,favoritos.countByUsuarioIdAndRotaId(aluno,r.id()));
    }
    @Test void favoritosEFlagsNaoVazamEntreContas() {
        var r=rota();Long a=usuario("PASSAGEIRO").getId(),b=usuario("PASSAGEIRO").getId();service.favoritar(a,r.id());service.remover(b,r.id());
        assertEquals(1,service.listar(a,0,20).total());assertEquals(0,service.listar(b,0,20).total());
        assertTrue(catalogo.buscar(filtro(),0,20,a,false).itens().stream().filter(v->v.id().equals(r.id())).findFirst().orElseThrow().favorita());
        assertFalse(catalogo.buscar(filtro(),0,20,b,false).itens().stream().filter(v->v.id().equals(r.id())).findFirst().orElseThrow().favorita());
    }
    @Test void despublicadaFicaOcultaESomenteReapareceSeRepublicada() {
        var r=rota();Long aluno=usuario("PASSAGEIRO").getId();service.favoritar(aluno,r.id());
        rotas.publicar(r.id(),r.dono(),false);assertEquals(0,service.listar(aluno,0,20).total());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.favoritar(aluno,r.id()));
        rotas.publicar(r.id(),r.dono(),true);assertEquals(1,service.listar(aluno,0,20).total());
        rotas.disponibilidade(r.id(),r.dono(),false);assertEquals(0,service.listar(aluno,0,20).total());service.remover(aluno,r.id());
    }
    @Test void paginacaoEFiltroDeFavoritasSeguemOrdemDoCatalogo() {
        var a=rota();var b=rota();Long aluno=usuario("PASSAGEIRO").getId();service.favoritar(aluno,b.id());service.favoritar(aluno,a.id());
        assertEquals(a.id(),service.listar(aluno,0,1).itens().get(0).id());assertEquals(b.id(),service.listar(aluno,1,1).itens().get(0).id());
        assertEquals(2,catalogo.buscar(filtro(),0,20,aluno,true).total());
        assertThrows(IllegalArgumentException.class,()->service.listar(aluno,0,51));
    }
    @Test void motoristaNaoGerenciaFavoritosDePassageiro() {
        var r=rota();assertThrows(AccessDeniedException.class,()->service.favoritar(r.dono(),r.id()));
        assertThrows(AccessDeniedException.class,()->service.listar(r.dono(),0,20));
    }
    private void corrida(Runnable a,Runnable b) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch prontos=new CountDownLatch(2),inicio=new CountDownLatch(1);
        java.util.function.Function<Runnable,Callable<Void>> tarefa=acao->()->{prontos.countDown();assertTrue(inicio.await(5,TimeUnit.SECONDS));acao.run();return null;};
        try{var x=pool.submit(tarefa.apply(a));var y=pool.submit(tarefa.apply(b));assertTrue(prontos.await(5,TimeUnit.SECONDS));inicio.countDown();x.get(15,TimeUnit.SECONDS);y.get(15,TimeUnit.SECONDS);}
        finally{inicio.countDown();pool.shutdownNow();}
    }
    @Test void favoritarConcorrentementeNaoDuplica() throws Exception {
        var r=rota();Long aluno=usuario("PASSAGEIRO").getId();corrida(()->service.favoritar(aluno,r.id()),()->service.favoritar(aluno,r.id()));
        assertEquals(1,favoritos.countByUsuarioIdAndRotaId(aluno,r.id()));assertTrue(service.favorita(aluno,r.id()));
    }
    @Test void favoritarERemoverSimultaneamenteMantemUmUnicoRegistro() throws Exception {
        var r=rota();Long aluno=usuario("PASSAGEIRO").getId();corrida(()->service.favoritar(aluno,r.id()),()->service.remover(aluno,r.id()));
        assertEquals(1,favoritos.countByUsuarioIdAndRotaId(aluno,r.id()));
        assertEquals(service.favorita(aluno,r.id())?1:0,service.listar(aluno,0,20).total());
    }
}
