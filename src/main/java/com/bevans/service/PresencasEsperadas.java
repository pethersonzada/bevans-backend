package com.bevans.service;
import java.util.*;
public final class PresencasEsperadas {
    private PresencasEsperadas(){}
    public static boolean inclui(String status,String sentido){
        if(status==null)return false;String s=status.trim().toUpperCase(Locale.ROOT);
        return Set.of("AMBOS","IDA_VOLTA","CONFIRMADO","VOU","PRESENTE").contains(s)||s.equals(sentido)||s.equals("SO_"+sentido);
    }
}
