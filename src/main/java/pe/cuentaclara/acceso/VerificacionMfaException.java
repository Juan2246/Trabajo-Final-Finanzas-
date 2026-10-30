package pe.cuentaclara.acceso;

import pe.cuentaclara.comun.ReglaNegocioException;

/** Un intento fallido debe quedar guardado aunque la operación no continúe. */
public class VerificacionMfaException extends ReglaNegocioException {
    public VerificacionMfaException(String mensaje) {
        super(mensaje, 400);
    }
}
