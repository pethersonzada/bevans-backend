package com.bevans.service;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Segredos de 256 bits; somente SHA-256 vai para persistência. */
public final class TokensExcursao {
    private static final SecureRandom RANDOM=new SecureRandom();
    private TokensExcursao(){}
    public static String gerar(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public static String hash(String token){
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Credencial inválida.");
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException("SHA-256 indisponível",ex);}
    }
}
