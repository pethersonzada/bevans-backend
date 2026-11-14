package com.bevans.repository;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.sql.init.mode=never"})
@Import({ColetaPrivadaService.class,PosicaoViagemService.class,RotaService.class,ViagemService.class,ColetaPrivadaIntegrationTest.Tempo.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ColetaPrivadaIntegrationTest {
    static class Relogio extends Clock {volatile Instant agora=Instant.now().plusSeconds(1);public Instant instant(){return agora;}public ZoneId getZone(){return ZoneId.of("America/Sao_Paulo");}public Clock withZone(ZoneId z){return this;}}
    @TestConfiguration static class Tempo {@Bean Clock clock(){return new Relogio();}}
    @Autowired ColetaPrivadaService service;@Autowired UsuarioRepository usuarios;@Autowired TurmaRepository turmas;
    @Autowired AlunoTurmaRepository vinculos;@Autowired EnderecoRepository enderecos;@Autowired PresencaRepository presencas;
    @Autowired EscolhaParadaPrivadaRepository escolhas;@Autowired ColetaPrivadaRepository coletas;@Autowired ViagemService viagens;
    @Autowired PosicaoViagemService gps;@Autowired RotaService rotas;@Autowired Clock clock;
    private record Cenario(Long id,String dono,String aluno,Long alunoId){}
    @BeforeEach void tempo(){((Relogio)clock).agora=Instant.now().plusSeconds(1);}
    private Usuario usuario(String tipo){String uid=UUID.randomUUID().toString();var u=new Usuario();u.setNome(uid);u.setTelefone(uid);u.setEmail(uid+"@test.invalid");u.setFirebaseUid(uid);u.setTipo(tipo);return usuarios.save(u);}
    private Cenario cenario(){var d=usuario("MOTORISTA");var a=usuario("PASSAGEIRO");var t=new Turma();t.setNome("Privada");t.setMotorista(d);t.setCapacidade(12);t.setOrigemNome("Garagem");t.setOrigemLatitude(-8.5);t.setOrigemLongitude(-35.5);t.setDestinoNome("Campus");t.setDestinoLatitude(-8.9);t.setDestinoLongitude(-35.9);t=turmas.save(t);var v=new AlunoTurma();v.setTurma(t);v.setAluno(a);v.setStatus("APROVADO");vinculos.save(v);return new Cenario(t.getId(),d.getFirebaseUid(),a.getFirebaseUid(),a.getId());}
    private ColetaPrivadaRequest.Parada ponto(Long id,String nome,double lat){return new ColetaPrivadaRequest.Parada(id,nome,lat,-35.0,LocalTime.of(7,0),true);}
    private ColetaPrivadaDTO configurar(Cenario c,String s){return service.configurar(c.id(),s,c.dono(),new ColetaPrivadaRequest(ModoColetaPrivada.PARADAS_DEFINIDAS,0L,List.of(ponto(null,"A",-8.0),ponto(null,"B",-8.1))));}
    private Endereco endereco(Cenario c,double lat){var e=new Endereco();e.setUsuario(usuarios.findById(c.alunoId()).orElseThrow());e.setRua("Residência privada");e.setLatitude(lat);e.setLongitude(-35.0);return enderecos.save(e);}
    private Presenca presenca(Cenario c,String status){var p=new Presenca();p.setTurma(turmas.findById(c.id()).orElseThrow());p.setUsuario(usuarios.findById(c.alunoId()).orElseThrow());p.setData(LocalDate.now(clock));p.setStatus(status);return presencas.save(p);}
    @Test void padraoPortaAPortaNaoAlteraTurmasOuEnderecos(){
        var c=cenario();var a=endereco(c,-8.01);var b=endereco(c,-8.11);var p=presenca(c,"AMBOS");p.setEnderecoIda(a);p.setEnderecoVolta(b);presencas.save(p);
        assertEquals(ModoColetaPrivada.PORTA_A_PORTA,service.consultar(c.id(),"IDA",c.aluno(),null).modo());
        assertTrue(service.consultar(c.id(),"VOLTA",c.aluno(),null).paradas().isEmpty());assertEquals(2,enderecos.findByUsuarioId(c.alunoId()).size());
        assertEquals(a.getId(),presencas.findByUsuarioIdAndTurmaIdAndData(c.alunoId(),c.id(),LocalDate.now(clock)).orElseThrow().getEnderecoIda().getId());
    }
    @Test void proximidadeNaoSubstituiEscolhaERecalculaDepoisDeAlterarEndereco(){
        var c=cenario();var cfg=configurar(c,"IDA");var a=endereco(c,-8.001);
        var mapa=service.consultar(c.id(),"IDA",c.aluno(),a.getId());assertEquals(cfg.paradas().get(0).id(),mapa.sugestao().parada().id());assertEquals("HAVERSINE_LINHA_RETA",mapa.sugestao().metodo());assertNull(mapa.escolhidaId());
        service.escolher(c.id(),"IDA",c.aluno(),null,cfg.paradas().get(1).id(),cfg.versao());
        a.setLatitude(-8.0);enderecos.save(a);mapa=service.consultar(c.id(),"IDA",c.aluno(),a.getId());assertEquals(0,mapa.sugestao().distanciaMetros(),0.001);assertEquals(cfg.paradas().get(1).id(),mapa.escolhidaId());
        assertNull(service.consultar(c.id(),"VOLTA",c.aluno(),null).escolhidaId());
    }
    @Test void idsEnderecoEEscolhasSaoIsoladosPorTitularTurmaESentido(){
        var c=cenario();var outro=cenario();var cfg=configurar(c,"IDA");var estrangeira=configurar(outro,"IDA");var a=endereco(outro,-8.0);
        assertThrows(AccessDeniedException.class,()->service.consultar(c.id(),"IDA",outro.aluno(),null));
        assertThrows(AccessDeniedException.class,()->service.configurar(c.id(),"IDA",outro.dono(),new ColetaPrivadaRequest(ModoColetaPrivada.PORTA_A_PORTA,cfg.versao(),List.of())));
        assertThrows(AccessDeniedException.class,()->service.consultar(c.id(),"IDA",c.aluno(),a.getId()));
        assertThrows(ResponseStatusException.class,()->service.escolher(c.id(),"IDA",c.aluno(),null,estrangeira.paradas().get(0).id(),cfg.versao()));
        assertThrows(AccessDeniedException.class,()->service.escolher(c.id(),"IDA",c.aluno(),outro.alunoId(),cfg.paradas().get(0).id(),cfg.versao()));
    }
    @Test void paradaUsadaNaoPodeSerMovidaRemovidaOuTerModoTrocado(){
        var c=cenario();var cfg=configurar(c,"IDA");service.escolher(c.id(),"IDA",c.aluno(),null,cfg.paradas().get(0).id(),cfg.versao());
        assertThrows(ResponseStatusException.class,()->service.configurar(c.id(),"IDA",c.dono(),new ColetaPrivadaRequest(ModoColetaPrivada.PORTA_A_PORTA,cfg.versao(),List.of())));
        service.removerEscolha(c.id(),"IDA",c.aluno(),null);var a=endereco(c,-8.0);
        var porta=service.configurar(c.id(),"IDA",c.dono(),new ColetaPrivadaRequest(ModoColetaPrivada.PORTA_A_PORTA,cfg.versao(),List.of()));
        assertEquals(ModoColetaPrivada.PORTA_A_PORTA,porta.modo());assertNotNull(enderecos.findById(a.getId()).orElseThrow());assertNull(porta.escolhidaId());
    }
    @Test void planejamentoUsaParadaEscolhidaEmVezDeResidenciaEPreservaOrdemVolta() throws Exception {
        var c=cenario();var ida=configurar(c,"IDA");var volta=configurar(c,"VOLTA");var casa=endereco(c,-8.9);
        service.escolher(c.id(),"IDA",c.aluno(),null,ida.paradas().get(1).id(),ida.versao());service.escolher(c.id(),"VOLTA",c.aluno(),null,volta.paradas().get(0).id(),volta.versao());
        var p=presenca(c,"AMBOS");p.setEnderecoIda(casa);p.setEnderecoVolta(casa);presencas.save(p);
        var rota=service.planejar(c.id(),"IDA",c.dono(),false);assertEquals(List.of("Garagem","A","B","Campus"),rota.stream().map(PontoColetaPrivadaDTO::nome).toList());
        assertEquals(-8.1,rotas.otimizarRota("IDA",c.id()).get(10,TimeUnit.SECONDS).get(0).getLatitude());
        assertEquals(List.of("Campus","B","A","Garagem"),service.planejar(c.id(),"VOLTA",c.dono(),false).stream().map(PontoColetaPrivadaDTO::nome).toList());
        assertEquals("Campus",service.planejar(c.id(),"IDA",c.dono(),true).get(3).nome());
        assertThrows(ResponseStatusException.class,()->service.removerEscolha(c.id(),"IDA",c.aluno(),null));
    }
    @Test void mapaMantemParadasDuranteViagemComGpsIndisponivelEExpirado(){
        var c=cenario();var cfg=configurar(c,"IDA");service.escolher(c.id(),"IDA",c.aluno(),null,cfg.paradas().get(0).id(),cfg.versao());presenca(c,"IDA");viagens.iniciarRota(c.id(),"IDA");
        var mapa=service.consultar(c.id(),"IDA",c.aluno(),null);assertNotNull(mapa.viagemId());assertEquals("SEM_LOCALIZACAO",mapa.gps().estado());assertEquals(2,mapa.paradas().size());
        ((Relogio)clock).agora=Instant.now().plusSeconds(1);gps.enviar(mapa.viagemId(),c.dono(),new PosicaoRequest("amostra",-8.2,-35.2,clock.instant(),5.0,null));
        assertEquals("ATUALIZADA",service.consultar(c.id(),"IDA",c.aluno(),null).gps().estado());((Relogio)clock).agora=clock.instant().plusSeconds(31);
        mapa=service.consultar(c.id(),"IDA",c.aluno(),null);assertEquals("DESATUALIZADA",mapa.gps().estado());assertNull(mapa.gps().latitude());assertEquals(cfg.paradas().get(0).id(),mapa.escolhidaId());
        assertThrows(ResponseStatusException.class,()->service.removerEscolha(c.id(),"IDA",c.aluno(),null));viagens.encerrarRota(c.id());assertNull(service.consultar(c.id(),"IDA",c.aluno(),null).viagemId());
    }
    @Test void validacaoDePinsVersaoEPlanosImpedeAlteracaoSilenciosa(){
        var c=cenario();assertThrows(IllegalArgumentException.class,()->service.configurar(c.id(),"IDA",c.dono(),new ColetaPrivadaRequest(ModoColetaPrivada.PARADAS_DEFINIDAS,0L,List.of())));
        var cfg=configurar(c,"IDA");assertThrows(ResponseStatusException.class,()->service.configurar(c.id(),"IDA",c.dono(),new ColetaPrivadaRequest(ModoColetaPrivada.PORTA_A_PORTA,0L,List.of())));
        presenca(c,"IDA");assertThrows(ResponseStatusException.class,()->service.configurar(c.id(),"IDA",c.dono(),new ColetaPrivadaRequest(ModoColetaPrivada.PORTA_A_PORTA,cfg.versao(),List.of())));
        assertThrows(ResponseStatusException.class,()->viagens.iniciarRota(c.id(),"IDA"));
    }
    @Test void empateDeDistanciaEEscolhasConcorrentesSaoDeterministicos() throws Exception {
        var c=cenario();var cfg=service.configurar(c.id(),"IDA",c.dono(),new ColetaPrivadaRequest(ModoColetaPrivada.PARADAS_DEFINIDAS,0L,List.of(ponto(null,"A",-8.0),ponto(null,"B",-8.0))));var a=endereco(c,-8.0);
        assertEquals(cfg.paradas().get(0).id(),service.consultar(c.id(),"IDA",c.aluno(),a.getId()).sugestao().parada().id());
        var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var x=pool.submit(()->{inicio.await();service.escolher(c.id(),"IDA",c.aluno(),null,cfg.paradas().get(0).id(),cfg.versao());return true;});
            var y=pool.submit(()->{inicio.await();service.escolher(c.id(),"IDA",c.aluno(),null,cfg.paradas().get(1).id(),cfg.versao());return true;});inicio.countDown();x.get(15,TimeUnit.SECONDS);y.get(15,TimeUnit.SECONDS);
            assertEquals(1,escolhas.findByColetaIdAndAtivaTrue(coletas.findByTurmaIdAndSentido(c.id(),"IDA").orElseThrow().getId()).size());
        }finally{inicio.countDown();pool.shutdownNow();}
    }
}
