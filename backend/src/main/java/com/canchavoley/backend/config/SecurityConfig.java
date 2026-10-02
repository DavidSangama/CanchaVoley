package com.canchavoley.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/canchas/**",
                                "/api/horarios/**",
                                "/api/reservas/fecha/**",
                                "/api/clientes/dni/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/clientes",
                                "/api/reservas",
                                "/api/pagos").permitAll()
                        .requestMatchers("/api/admin/authenticate").authenticated()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService adminUserDetailsService(
            @Value("${ADMIN_USERNAME}") String username,
            @Value("${ADMIN_PASSWORD}") String password,
            PasswordEncoder passwordEncoder) {
        if (username.isBlank() || password.length() < 12) {
            throw new IllegalArgumentException("Configure un ADMIN_USERNAME y un ADMIN_PASSWORD de al menos 12 caracteres.");
        }

        return new InMemoryUserDetailsManager(
                User.withUsername(username.trim())
                        .password(passwordEncoder.encode(password))
                        .roles("ADMIN")
                        .build());
    }
}
