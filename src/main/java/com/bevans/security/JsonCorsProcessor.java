package com.bevans.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

public class JsonCorsProcessor extends DefaultCorsProcessor {

    private final ThreadLocal<HttpServletRequest> requisicao =
            new ThreadLocal<>();

    @Override
    public boolean processRequest(
            CorsConfiguration config,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        requisicao.set(request);
        try {
            return super.processRequest(config, request, response);
        } finally {
            requisicao.remove();
        }
    }

    @Override
    protected void rejectRequest(ServerHttpResponse response)
            throws IOException {
        HttpServletResponse servletResponse =
                ((ServletServerHttpResponse) response).getServletResponse();

        HttpErrorWriter.write(
                requisicao.get(),
                servletResponse,
                403,
                "Requisição CORS não permitida."
        );
    }
}
