package pe.cuentaclara.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

/** El token pertenece a la sesión persistida, no al estado local del navegador. */
public class SessionCsrfTokenRepository implements CsrfTokenRepository {
    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken("X-CSRF-Token", "_csrf", RandomTokens.token(32));
    }

    @Override
    public void saveToken(
            CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        // AuthService crea y elimina el token junto con su sesión.
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user)
            return new DefaultCsrfToken("X-CSRF-Token", "_csrf", user.csrf());
        return null;
    }
}
