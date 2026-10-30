package pe.cuentaclara.comun;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<?> archivoGrande(Exception error) {
        return response(
                413, "La imagen supera el tamaño permitido. Usa un JPG o PNG de hasta 5 MB.");
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ResponseEntity<?> rubro(ReglaNegocioException error) {
        return response(error.getStatus(), error.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException error) {
        var field = error.getBindingResult().getFieldErrors().getFirst();
        return response(400, "Revisa " + field.getField() + ": " + field.getDefaultMessage());
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class,
        ConstraintViolationException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<?> invalid(Exception error) {
        return response(400, "Revisa los campos enviados y sus formatos.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> notFound(Exception error) {
        return response(404, "No se encontró la página o recurso.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method(Exception error) {
        return response(405, "Ese método no está disponible para esta ruta.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<?> media(Exception error) {
        return response(415, "Envía los datos en formato JSON.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> duplicate(DataIntegrityViolationException error) {
        return response(409, "Ya existe un usuario o documento con esos datos.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> forbidden(AccessDeniedException error) {
        return response(403, "Tu perfil no permite esta operación.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception error) {
        log.error("No se pudo completar la operación", error);
        return response(500, "No se pudo completar la operación. Vuelve a intentarlo.");
    }

    private ResponseEntity<?> response(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
