package com.bevans.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
@Configuration
public class TempoConfig {
    @Bean
    public Clock clock(@Value("${app.time-zone:America/Sao_Paulo}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
