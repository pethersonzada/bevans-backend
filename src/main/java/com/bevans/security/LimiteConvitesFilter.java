package com.bevans.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;
/** IP direto + teto global por VM. Não confia em X-Forwarded-For do cliente. */
public class LimiteConvitesFilter extends OncePerRequestFilter {
    private final Clock clock;private final Map<String,Integer> ips=new HashMap<>();private Instant inicio;private int total;
    public LimiteConvitesFilter(Clock clock){this.clock=clock;inicio=clock.instant();}
    private synchronized boolean permitir(String ip){
        if(!clock.instant().isBefore(inicio.plusSeconds(60))){ips.clear();total=0;inicio=clock.instant();}
        if(total>=600||ips.getOrDefault(ip,0)>=60||(!ips.containsKey(ip)&&ips.size()>=5000))return false;
        ips.merge(ip,1,Integer::sum);total++;return true;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest r){return !r.getRequestURI().startsWith(r.getContextPath()+"/publico/excursoes/")||"OPTIONS".equals(r.getMethod());}
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException{
        s.setHeader("Cache-Control","no-store");
        if(!permitir(Objects.toString(r.getRemoteAddr(),"unknown"))){s.setHeader("Retry-After","60");HttpErrorWriter.write(r,s,429,"Limite temporário de solicitações. Tente novamente mais tarde.");return;}
        chain.doFilter(r,s);
    }
}
