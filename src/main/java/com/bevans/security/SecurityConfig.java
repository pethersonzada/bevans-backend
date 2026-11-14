package com.bevans.security;

import java.util.List;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final FirebaseAuthenticationFilter firebaseFilter;
    private final String allowedOrigins;

    public SecurityConfig(
            FirebaseAuthenticationFilter firebaseFilter,
            @org.springframework.beans.factory.annotation.Value(
                    "${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8081,http://localhost:19006}"
            ) String allowedOrigins) {
        this.firebaseFilter = firebaseFilter;
        this.allowedOrigins = allowedOrigins;
    }

    // Executar o filtro Firebase apenas dentro da cadeia Spring Security.
    @Bean
    public FilterRegistrationBean<FirebaseAuthenticationFilter> firebaseFilterRegistration(
            FirebaseAuthenticationFilter filter) {
        FilterRegistrationBean<FirebaseAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean(name = "corsFilter")
    public org.springframework.web.filter.CorsFilter corsFilter() {
        org.springframework.web.filter.CorsFilter filter =
                new org.springframework.web.filter.CorsFilter(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOrigins(
                            Arrays.stream(allowedOrigins.split(","))
                                    .map(String::trim)
                                    .filter(origin -> !origin.isBlank())
                                    .toList()
                    );
                    config.setAllowedMethods(
                            List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    );
                    config.setAllowedHeaders(
                            List.of("Authorization", "Content-Type",
                                    "Accept", "Bypass-Tunnel-Reminder", "X-Convite", "X-Inscricao")
                    );
                    return config;
                });

        filter.setCorsProcessor(new JsonCorsProcessor());
        return filter;
    }

    @Bean
    public FilterRegistrationBean<org.springframework.web.filter.CorsFilter>
    corsFilterRegistration(
            org.springframework.web.filter.CorsFilter corsFilter
    ) {
        FilterRegistrationBean<org.springframework.web.filter.CorsFilter>
                registration = new FilterRegistrationBean<>(corsFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(org.springframework.security.config.Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, ex) ->
                        HttpErrorWriter.write(
                                request, response, 401,
                                "Autenticação obrigatória."
                        ))
                .accessDeniedHandler((request, response, ex) ->
                        HttpErrorWriter.write(
                                request, response, 403,
                                "Acesso negado ao recurso."
                        ))
            )
            .authorizeHttpRequests(a -> a
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/health").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/publico/excursoes/convite").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/publico/excursoes/credenciais","/publico/excursoes/inscricoes").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/publico/excursoes/inscricao").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/publico/excursoes/inscricao/notificacoes").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.PUT,"/publico/excursoes/inscricao/notificacoes/*/leitura").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/publico/excursoes/inscricao").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/publico/excursoes/operacao/*/minha","/publico/excursoes/operacao/*/minha/historico").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/publico/excursoes/operacao/*/localizacao","/publico/excursoes/operacao/*/localizacao/eventos").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.PUT,"/publico/excursoes/operacao/*/minha/estado").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new LimiteConvitesFilter(java.time.Clock.systemUTC()),FirebaseAuthenticationFilter.class);
        return http.build();
    }
}
