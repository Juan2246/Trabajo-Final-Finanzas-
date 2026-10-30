package pe.cuentaclara.acceso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

/** El token pertenece a la sesión persistida, no al estado local del navegador. */
public class SesionCsrfTokenRepository implements CsrfTokenRepository {
    @Override
    public CsrfToken generateToken(HttpServletRequest solicitud) {
        return new DefaultCsrfToken("X-CSRF-Token", "_csrf", TokensAleatoriosUtil.token(32));
    }

    @Override
    public void saveToken(
            CsrfToken token, HttpServletRequest solicitud, HttpServletResponse response) {
        // AccesoService crea y elimina el token junto con su sesión.
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest solicitud) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof UsuarioActualPrincipal usuario)
            return new DefaultCsrfToken("X-CSRF-Token", "_csrf", usuario.csrf());
        return null;
    }
}
