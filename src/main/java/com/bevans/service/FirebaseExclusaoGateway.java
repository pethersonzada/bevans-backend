package com.bevans.service;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class FirebaseExclusaoGateway implements ExclusaoIdentidadeGateway {
    private final boolean habilitado;
    public FirebaseExclusaoGateway(@Value("${app.contas.exclusao-remota.enabled:false}")boolean h){habilitado=h;}
    public Resultado excluir(String uid){
        if(!habilitado||FirebaseApp.getApps().isEmpty())return Resultado.DESATIVADO;
        try{FirebaseAuth.getInstance().deleteUser(uid);return Resultado.EXCLUIDA;}
        catch(FirebaseAuthException e){
            if(e.getAuthErrorCode()==AuthErrorCode.USER_NOT_FOUND)return Resultado.EXCLUIDA;
            if(e.getErrorCode()==com.google.firebase.ErrorCode.UNAVAILABLE||e.getErrorCode()==com.google.firebase.ErrorCode.INTERNAL||e.getErrorCode()==com.google.firebase.ErrorCode.RESOURCE_EXHAUSTED||e.getErrorCode()==com.google.firebase.ErrorCode.DEADLINE_EXCEEDED)return Resultado.TRANSITORIO;
            return Resultado.PERMANENTE;
        }
    }
}
