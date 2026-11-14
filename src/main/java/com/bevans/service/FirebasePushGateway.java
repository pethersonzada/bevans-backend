package com.bevans.service;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class FirebasePushGateway implements PushGateway {
    private final boolean habilitado;
    public FirebasePushGateway(@Value("${app.push.enabled:false}")boolean h){habilitado=h;}
    @Override public Resultado enviar(String token,Long id,String titulo,String mensagem){
        if(!habilitado||FirebaseApp.getApps().isEmpty())return Resultado.INDISPONIVEL;
        try{
            FirebaseMessaging.getInstance().send(Message.builder().setToken(token)
                .setNotification(Notification.builder().setTitle(titulo).setBody(mensagem).build())
                .putData("notificacaoId",id.toString()).build());
            return Resultado.ENVIADO;
        }catch(FirebaseMessagingException e){
            if(e.getMessagingErrorCode()==MessagingErrorCode.UNREGISTERED)return Resultado.TOKEN_INVALIDO;
            if(e.getMessagingErrorCode()==MessagingErrorCode.UNAVAILABLE||e.getMessagingErrorCode()==MessagingErrorCode.INTERNAL||e.getMessagingErrorCode()==MessagingErrorCode.QUOTA_EXCEEDED)return Resultado.TRANSITORIO;
            return Resultado.PERMANENTE;
        }catch(IllegalArgumentException e){return Resultado.PERMANENTE;}
    }
}
