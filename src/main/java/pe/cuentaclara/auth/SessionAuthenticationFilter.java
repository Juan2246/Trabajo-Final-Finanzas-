package pe.cuentaclara.auth;

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

public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final SessionService sessions;

    public SessionAuthenticationFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/") && request.getCookies() != null) {
            String token =
                    Arrays.stream(request.getCookies())
                            .filter(c -> c.getName().equals("cc_session"))
                            .map(jakarta.servlet.http.Cookie::getValue)
                            .findFirst()
                            .orElse(null);
            sessions.lookup(token)
                    .ifPresent(
                            user -> {
                                var authority =
                                        new SimpleGrantedAuthority(
                                                "ROLE_"
                                                        + user.role()
                                                                .toUpperCase(
                                                                        java.util.Locale.ROOT));
                                var authentication =
                                        new UsernamePasswordAuthenticationToken(
                                                user, null, List.of(authority));
                                SecurityContextHolder.getContext()
                                        .setAuthentication(authentication);
                            });
        }
        chain.doFilter(request, response);
    }
}
