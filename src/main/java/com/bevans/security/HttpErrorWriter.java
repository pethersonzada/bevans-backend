package com.bevans.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpErrorWriter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpErrorWriter() {}

    public static void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String mensagem
    ) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status);
        body.put("erro", mensagem);
        body.put("caminho", request.getRequestURI());

        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        JSON.writeValue(response.getWriter(), body);
    }
}
