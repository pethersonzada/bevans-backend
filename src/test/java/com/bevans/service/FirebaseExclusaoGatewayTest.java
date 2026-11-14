package com.bevans.service;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class FirebaseExclusaoGatewayTest {
    @Test void gatewayDesabilitadoNaoExigeCredenciais(){assertEquals(ExclusaoIdentidadeGateway.Resultado.DESATIVADO,new FirebaseExclusaoGateway(false).excluir("uid-sintetico"));}
    @Test void contaJaAusenteNoProvedorETratadaIdempotentemente()throws Exception{
        var auth=mock(FirebaseAuth.class);var erro=mock(FirebaseAuthException.class);when(erro.getAuthErrorCode()).thenReturn(AuthErrorCode.USER_NOT_FOUND);doThrow(erro).when(auth).deleteUser("uid-sintetico");
        try(var apps=mockStatic(FirebaseApp.class);var autent=mockStatic(FirebaseAuth.class)){apps.when(FirebaseApp::getApps).thenReturn(List.of(mock(FirebaseApp.class)));autent.when(FirebaseAuth::getInstance).thenReturn(auth);assertEquals(ExclusaoIdentidadeGateway.Resultado.EXCLUIDA,new FirebaseExclusaoGateway(true).excluir("uid-sintetico"));}
    }
}
