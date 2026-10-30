package pe.cuentaclara.acceso;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class AutenticacionSesionFilter extends OncePerRequestFilter {
    private final SesionService sesiones;

    public AutenticacionSesionFilter(SesionService sesiones) {
        this.sesiones = sesiones;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest solicitud, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (solicitud.getRequestURI().startsWith("/api/") && solicitud.getCookies() != null) {
            String token =
                    Arrays.stream(solicitud.getCookies())
                            .filter(c -> c.getName().equals("cc_session"))
                            .map(jakarta.servlet.http.Cookie::getValue)
                            .findFirst()
                            .orElse(null);
            sesiones.buscarSesion(token)
                    .ifPresent(
                            usuario -> {
                                var authority =
                                        new SimpleGrantedAuthority(
                                                "ROLE_"
                                                        + usuario.role()
                                                                .toUpperCase(
                                                                        java.util.Locale.ROOT));
                                var authentication =
                                        new UsernamePasswordAuthenticationToken(
                                                usuario, null, List.of(authority));
                                SecurityContextHolder.getContext()
                                        .setAuthentication(authentication);
                            });
        }
        chain.doFilter(solicitud, response);
    }
}
