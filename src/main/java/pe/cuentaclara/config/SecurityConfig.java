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

import pe.cuentaclara.acceso.AutenticacionSesionFilter;
import pe.cuentaclara.acceso.SesionCsrfTokenRepository;
import pe.cuentaclara.acceso.SesionService;

import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, SesionService sesiones, ObjectMapper json)
            throws Exception {
        return http.sessionManagement(
                        config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilterBefore(new AutenticacionSesionFilter(sesiones), CsrfFilter.class)
                .csrf(
                        config ->
                                config.csrfTokenRepository(new SesionCsrfTokenRepository())
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        .ignoringRequestMatchers("/api/login", "/api/login/mfa"))
                .authorizeHttpRequests(
                        acceso ->
                                acceso.requestMatchers(
                                                "/api/health",
                                                "/api/challenge",
                                                "/api/login",
                                                "/api/login/mfa")
                                        .permitAll()
                                        .requestMatchers("/api/**")
                                        .authenticated()
                                        .anyRequest()
                                        .permitAll())
                .exceptionHandling(
                        config ->
                                config.authenticationEntryPoint(
                                                (solicitud, response, error) -> {
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
                                                (solicitud, response, error) -> {
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
