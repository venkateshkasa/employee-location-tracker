package com.employeetracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

         http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(
                                "/api/auth/login",
                                "/api/auth/logout",
                                "/api/auth/forgot-password",
                                "/api/auth/setup-password"
                        ))

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/icons/**",
                                "/uploads/**",
                                "/manifest.json",
                                "/service-worker.js",
                                "/favicon.ico",
                                "/setup-password",
                                "/setup-password.html"
                        ).permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/setup-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/validate-setup-token").permitAll()

                        // The dashboard must never be reachable without a valid, authenticated session.
                        .requestMatchers("/dashboard", "/dashboard.html").authenticated()

                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        .requestMatchers("/api/**").authenticated()

                        .anyRequest().authenticated()
                )

                .formLogin(form -> form.disable())

                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
