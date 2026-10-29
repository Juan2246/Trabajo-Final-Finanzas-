package pe.cuentaclara.auth;

import pe.cuentaclara.shared.BusinessException;

/** Un fallo de acceso conserva el desafío consumido y el contador de intentos. */
public class LoginFailedException extends BusinessException {
    public LoginFailedException() {
        super("Revisa usuario, contraseña y código de verificación.", 401);
    }
}
