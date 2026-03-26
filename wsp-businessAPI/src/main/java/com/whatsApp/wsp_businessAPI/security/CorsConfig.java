package com.whatsApp.wsp_businessAPI.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {

        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {

                registry.addMapping("/**")
                        // YOUR FRONTEND URLS ONLY 
                        .allowedOrigins(
                                "http://localhost:3000",
                                "http://localhost:5173",
                                "https://conclusional-unprimly-brendon.ngrok-free.dev",
                                "http://conclusional-unprimly-brendon.ngrok-free.dev"
                        )
                        .allowedMethods("GET","POST","PUT","DELETE","OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("Authorization")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}