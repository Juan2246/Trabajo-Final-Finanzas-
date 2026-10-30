package pe.cuentaclara.comun;

public class ReglaNegocioException extends RuntimeException {
    private final int status;

    public ReglaNegocioException(String message) {
        this(message, 400);
    }

    public ReglaNegocioException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
