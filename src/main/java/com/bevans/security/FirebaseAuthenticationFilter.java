package com.bevans.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null) {
            if (!header.startsWith("Bearer ") || header.substring(7).isBlank()) {
                unauthorized(request, response);
                return;
            }

            try {
                FirebaseToken token = FirebaseAuth.getInstance()
                        .verifyIdToken(header.substring(7));

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                token.getUid(),
                                null,
                                List.of()
                        );

                SecurityContextHolder.getContext().setAuthentication(auth);
                request.setAttribute("firebaseEmail", token.getEmail());
                Object exp=token.getClaims().get("exp");
                if(exp instanceof Number n)request.setAttribute("firebaseExpiresAt",java.time.Instant.ofEpochSecond(n.longValue()));

            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                unauthorized(request, response);
                return;
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void unauthorized(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        HttpErrorWriter.write(
                request, response, 401,
                "Token Firebase ausente, inválido ou expirado."
        );
    }
}
