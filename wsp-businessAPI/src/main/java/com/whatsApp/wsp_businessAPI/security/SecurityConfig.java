package com.whatsApp.wsp_businessAPI.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.whatsApp.wsp_businessAPI.repository.RevokedTokenRepository;
import com.whatsApp.wsp_businessAPI.service.JwtService;

import lombok.RequiredArgsConstructor;


//@Configuration
//@EnableWebSecurity
//public class SecurityConfig {
//	
//	 @Bean
//	    public PasswordEncoder passwordEncoder() {
//	        return new BCryptPasswordEncoder();
//	    }
//
//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//
//        http
//            .csrf(csrf -> csrf.disable())
//            .authorizeHttpRequests(auth -> auth
//                    .anyRequest().permitAll()   // <-- everything open
//            )
//            .httpBasic(basic -> basic.disable())
//            .formLogin(form -> form.disable());
//
//        return http.build();
//    }
//}





@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokenRepository;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // ===== PUBLIC STATIC FILES - ADD THIS =====
                        .requestMatchers("/", "/index.html", "/whatsapp-onboarding.html", 
                                         "/whatsapp-message-demo.html", "/error").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                        
                        // ===== VERY IMPORTANT (preflight requests) =====
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ===== PUBLIC AUTH =====
                        .requestMatchers("/auth/**").permitAll()

                        // ===== WHATSAPP WEBHOOK =====
                        .requestMatchers("/webhook", "/webhook/**").permitAll()

                        // ===== EVERYTHING ELSE REQUIRES JWT =====
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, revokedTokenRepository),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}