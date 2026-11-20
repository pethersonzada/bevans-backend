package com.bevans.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limites de visitantes públicos por origem e tipo de operação.
 * Só aceita X-Real-IP quando o remetente é um proxy explicitamente confiável.
 */
public class LimiteConvitesFilter extends OncePerRequestFilter {
    private static final int LIMITE_PADRAO = 60;
    private static final int LIMITE_CONSULTA = 120;
    private static final int LIMITE_GLOBAL = 600;
    private static final int MAX_CHAVES = 5000;

    private final Clock clock;
    private final Set<String> proxiesConfiaveis;
    private final Map<String, Integer> contagens = new HashMap<>();
    private Instant inicio;
    private int total;

    public LimiteConvitesFilter(Clock clock) {
        this(clock, Set.of());
    }

    public LimiteConvitesFilter(Clock clock, Set<String> proxies) {
        this.clock = Objects.requireNonNull(clock);
        var ips = new HashSet<String>();
        for (String proxy : proxies) {
            String ip = literalIp(proxy);
            if (ip == null) {
                throw new IllegalArgumentException("IP de proxy confiável inválido.");
            }
            ips.add(ip);
        }
        this.proxiesConfiaveis = Set.copyOf(ips);
        this.inicio = clock.instant();
    }

    /** Converte apenas endereços literais, sem DNS, listas ou nomes de host. */
    private static String literalIp(String valor) {
        if (valor == null || valor.length() > 45 || !valor.equals(valor.trim())) {
            return null;
        }
        if (valor.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) {
            for (String parte : valor.split("\\.")) {
                if (Integer.parseInt(parte) > 255) return null;
            }
            return valor;
        }
        if (valor.indexOf(':') < 0 || !valor.matches("[0-9a-fA-F:]+")) {
            return null;
        }
        try {
            InetAddress endereco = InetAddress.getByName(valor);
            return endereco instanceof Inet6Address ? endereco.getHostAddress() : null;
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private String origem(HttpServletRequest request) {
        String remotoBruto = Objects.toString(request.getRemoteAddr(), "unknown");
        String remoto = literalIp(remotoBruto);
        if (remoto == null) return remotoBruto;
        if (!proxiesConfiaveis.contains(remoto)) return remoto;

        Enumeration<String> cabecalhos = request.getHeaders("X-Real-IP");
        if (!cabecalhos.hasMoreElements()) return remoto;
        String primeiro = cabecalhos.nextElement();
        if (cabecalhos.hasMoreElements()) return remoto;

        String real = literalIp(primeiro);
        return real == null ? remoto : real;
    }

    private String classe(HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        // Leituras operacionais não disputam a quota de escrita/convites.
        if ("GET".equals(request.getMethod())
                && (uri.startsWith("/publico/excursoes/inscricao/")
                    || uri.startsWith("/publico/excursoes/operacao/"))) {
            return "CONSULTA";
        }
        return "PADRAO";
    }

    private synchronized boolean permitir(String ip, String classe) {
        if (!clock.instant().isBefore(inicio.plusSeconds(60))) {
            contagens.clear();
            total = 0;
            inicio = clock.instant();
        }
        int limite = "CONSULTA".equals(classe) ? LIMITE_CONSULTA : LIMITE_PADRAO;
        String chave = classe + "|" + ip;
        if (total >= LIMITE_GLOBAL || contagens.getOrDefault(chave, 0) >= limite
                || (!contagens.containsKey(chave) && contagens.size() >= MAX_CHAVES)) {
            return false;
        }
        contagens.merge(chave, 1, Integer::sum);
        total++;
        return true;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(
                request.getContextPath() + "/publico/excursoes/")
                || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        if (!permitir(origem(request), classe(request))) {
            response.setHeader("Retry-After", "60");
            HttpErrorWriter.write(request, response, 429,
                    "Limite temporário de solicitações. Tente novamente mais tarde.");
            return;
        }
        chain.doFilter(request, response);
    }
}
