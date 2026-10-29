package pe.cuentaclara.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import pe.cuentaclara.auth.SessionAuthenticationFilter;
import pe.cuentaclara.auth.SessionCsrfTokenRepository;
import pe.cuentaclara.auth.SessionService;

import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, SessionService sessions, ObjectMapper json)
            throws Exception {
        return http.sessionManagement(
                        config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilterBefore(new SessionAuthenticationFilter(sessions), CsrfFilter.class)
                .csrf(
                        config ->
                                config.csrfTokenRepository(new SessionCsrfTokenRepository())
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        .ignoringRequestMatchers("/api/login"))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/api/health", "/api/challenge", "/api/login")
                                        .permitAll()
                                        .requestMatchers("/api/**")
                                        .authenticated()
                                        .anyRequest()
                                        .permitAll())
                .exceptionHandling(
                        config ->
                                config.authenticationEntryPoint(
                                                (request, response, error) -> {
                                                    response.setStatus(401);
                                                    response.setContentType(
                                                            "application/json;charset=UTF-8");
                                                    json.writeValue(
                                                            response.getOutputStream(),
                                                            Map.of(
                                                                    "error",
                                                                    "Inicia sesión para"
                                                                            + " continuar."));
                                                })
                                        .accessDeniedHandler(
                                                (request, response, error) -> {
                                                    response.setStatus(403);
                                                    response.setContentType(
                                                            "application/json;charset=UTF-8");
                                                    json.writeValue(
                                                            response.getOutputStream(),
                                                            Map.of(
                                                                    "error",
                                                                    "No tienes permiso o la sesión"
                                                                            + " cambió. Recarga la"
                                                                            + " página."));
                                                }))
                .headers(
                        headers ->
                                headers.frameOptions(frame -> frame.deny())
                                        .contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'self'; img-src 'self'"
                                                                        + " https: data:; style-src"
                                                                        + " 'self' 'unsafe-inline';"
                                                                        + " script-src 'self';"
                                                                        + " connect-src 'self';"
                                                                        + " frame-ancestors 'none';"
                                                                        + " form-action 'self'")))
                .build();
    }
}
