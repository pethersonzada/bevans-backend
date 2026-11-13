package com.bevans.repository;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.sql.init.mode=never"})
@Import({CatalogoService.class,RotaPublicaService.class,TurmaService.class})
class CatalogoIntegrationTest {
    @Autowired CatalogoService service;
    @Autowired RotaPublicaService rotas;
    @Autowired TurmaService turmas;
    @Autowired TestEntityManager em;
    @MockBean GeocodingService geocoding;
    private Usuario usuario(String tipo) {
        String id=UUID.randomUUID().toString();Usuario u=new Usuario();u.setNome("Motorista público");u.setEmail(id+"@example.test");u.setTelefone(id);u.setTipo(tipo);return em.persist(u);
    }
    private RotaPublicaDTO criar(String nome,String cidade,CategoriaRota categoria,boolean publicar,int cap) {
        Usuario u=usuario("MOTORISTA");Turma t=new Turma();t.setNome("Privada");t.setTurno("Manhã");t.setCapacidade(cap);t.setMotorista(u);em.persist(t);
        var d=new RotaPublicaRequest(t.getId(),nome,cidade,categoria,ModoRota.FIXA,"Terminal",-8.2,-35.9,"Universidade São José",-8.3,-36.0,null,"Região Norte","Campus Saúde",Set.of(DayOfWeek.MONDAY),LocalTime.of(7,0),null);
        var r=rotas.criar(u.getId(),d);return publicar?rotas.publicar(r.id(),u.getId(),true):r;
    }
    private FiltroCatalogo vazio(){return new FiltroCatalogo(null,null,null,null,null,null,null,null,false);}
    @Test void somentePublicadasAtivasESemTurmasExcluidas() {
        var a=criar("A","Caruaru",CategoriaRota.UNIVERSITARIA,true,12);
        criar("Privada","Caruaru",CategoriaRota.UNIVERSITARIA,false,12);
        var inativa=criar("Inativa","Caruaru",CategoriaRota.UNIVERSITARIA,true,12);
        var i=em.find(RotaPublica.class,inativa.id());rotas.disponibilidade(i.getId(),i.getTurma().getMotorista().getId(),false);
        var excluida=criar("Excluída","Caruaru",CategoriaRota.UNIVERSITARIA,true,12);em.find(Turma.class,excluida.turmaId()).setExcluido(true);em.flush();
        var pagina=service.buscar(vazio(),0,20);assertEquals(1,pagina.total());assertEquals(a.id(),pagina.itens().get(0).id());
    }
    @Test void filtrosCombinadosNormalizamAcentosEMaiusculas() {
        var a=criar("Ônibus Saúde","São José",CategoriaRota.UNIVERSITARIA,true,12);criar("Outra","Caruaru",CategoriaRota.ESCOLAR,true,12);
        var f=new FiltroCatalogo("ONIBUS","sao jose","SAO JOSE","SAUDE","NORTE","MANHA",CategoriaRota.UNIVERSITARIA,DayOfWeek.MONDAY,true);
        var p=service.buscar(f,0,20);assertEquals(1,p.total());assertEquals(a.id(),p.itens().get(0).id());
        assertEquals(0,service.buscar(new FiltroCatalogo("inexistente",null,null,null,null,null,null,null,false),0,20).total());
    }
    @Test void paginaEstavelSemRepetirItens() {
        var a=criar("Mesmo","Caruaru",CategoriaRota.OUTROS,true,12);var b=criar("Mesmo","Caruaru",CategoriaRota.OUTROS,true,12);var c=criar("Mesmo","Caruaru",CategoriaRota.OUTROS,true,12);
        var p0=service.buscar(vazio(),0,2);var p1=service.buscar(vazio(),1,2);
        assertEquals(3,p0.total());assertEquals(2,p0.totalPaginas());assertEquals(List.of(a.id(),b.id()),p0.itens().stream().map(CatalogoRotaDTO::id).toList());
        assertEquals(c.id(),p1.itens().get(0).id());assertTrue(service.buscar(vazio(),2,2).itens().isEmpty());
    }
    @Test void filtroVagasContaSomenteVinculosAprovadosAtivos() {
        var r=criar("Lotada","Caruaru",CategoriaRota.OUTROS,true,1);Turma t=em.find(Turma.class,r.turmaId());
        AlunoTurma v=new AlunoTurma();v.setTurma(t);v.setAluno(usuario("PASSAGEIRO"));v.setStatus("APROVADO");em.persistAndFlush(v);
        var livre=new FiltroCatalogo(null,null,null,null,null,null,null,null,true);
        assertEquals(0,service.buscar(livre,0,20).total());assertEquals(0,service.buscar(vazio(),0,20).itens().get(0).vagas().disponiveis());
        v.setExcluido(true);em.flush();assertEquals(1,service.buscar(livre,0,20).total());
    }
    @Test void curingasSaoLiteraisEPaginacaoEValidada() {
        criar("Rota normal","Caruaru",CategoriaRota.OUTROS,true,12);
        assertEquals(0,service.buscar(new FiltroCatalogo("%",null,null,null,null,null,null,null,false),0,20).total());
        assertThrows(IllegalArgumentException.class,()->service.buscar(vazio(),-1,20));
        assertThrows(IllegalArgumentException.class,()->service.buscar(vazio(),0,51));
    }
}
