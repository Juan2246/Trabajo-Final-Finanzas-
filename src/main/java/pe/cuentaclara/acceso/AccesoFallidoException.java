package pe.cuentaclara.acceso;

import pe.cuentaclara.comun.ReglaNegocioException;

/** Un fallo de acceso conserva el desafío consumido y el contador de intentos. */
public class AccesoFallidoException extends ReglaNegocioException {
    public AccesoFallidoException() {
        super("Revisa usuario, contraseña y código de verificación.", 401);
    }
}
