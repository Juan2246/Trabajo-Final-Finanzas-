package pe.cuentaclara.catalogo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import pe.cuentaclara.acceso.TokensAleatoriosUtil;
import pe.cuentaclara.acceso.UsuarioActualPrincipal;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.comun.RelojNegocioComponent;
import pe.cuentaclara.tiendas.AccesoTiendaService;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

@Service
public class ImagenProductoService {
    public static final Set<String> REFERENCIAS =
            Set.of(
                    "/catalogo/arroz.jpg",
                    "/catalogo/aceite.jpg",
                    "/catalogo/leche.jpg",
                    "/catalogo/reparto.jpg",
                    "/catalogo/producto.jpg");
    private final ImagenProductoRepository imagenes;
    private final ProductoRepository productos;
    private final AccesoTiendaService acceso;
    private final RelojNegocioComponent reloj;

    public ImagenProductoService(
            ImagenProductoRepository imagenes,
            ProductoRepository productos,
            AccesoTiendaService acceso,
            RelojNegocioComponent reloj) {
        this.imagenes = imagenes;
        this.productos = productos;
        this.acceso = acceso;
        this.reloj = reloj;
    }

    @Transactional
    public Map<String, Object> subir(
            UsuarioActualPrincipal usuario, Long tiendaId, MultipartFile archivo) {
        if (usuario.esCliente())
            throw new ReglaNegocioException("Solo el comercio administra las imágenes.", 403);
        var tienda = acceso.resolverTienda(usuario, tiendaId, true);
        if (archivo.isEmpty() || archivo.getSize() > 5 * 1024 * 1024)
            throw new ReglaNegocioException("Selecciona una imagen JPG o PNG de hasta 5 MB.");
        if (!Set.of("image/jpeg", "image/png")
                .contains(archivo.getContentType() == null ? "" : archivo.getContentType()))
            throw new ReglaNegocioException("Solo se permiten imágenes JPG o PNG.");
        try (var entrada = ImageIO.createImageInputStream(archivo.getInputStream())) {
            var lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext())
                throw new ReglaNegocioException("El archivo no contiene una imagen válida.");
            var lector = lectores.next();
            BufferedImage original;
            try {
                lector.setInput(entrada, true, true);
                String formato = lector.getFormatName();
                if (!formato.equalsIgnoreCase("png") && !formato.equalsIgnoreCase("jpeg"))
                    throw new ReglaNegocioException("Solo se permiten imágenes JPG o PNG.");
                int ancho = lector.getWidth(0), alto = lector.getHeight(0);
                if (ancho < 16
                        || alto < 16
                        || ancho > 12000
                        || alto > 12000
                        || (long) ancho * alto > 12_000_000)
                    throw new ReglaNegocioException(
                            "Usa una imagen de al menos 16 × 16 y hasta 12 megapíxeles.");
                original = lector.read(0);
            } finally {
                lector.dispose();
            }
            double escala =
                    Math.min(1d, 1600d / Math.max(original.getWidth(), original.getHeight()));
            int ancho = Math.max(1, (int) (original.getWidth() * escala)),
                    alto = Math.max(1, (int) (original.getHeight() * escala));
            BufferedImage limpia = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            Graphics2D dibujo = limpia.createGraphics();
            try {
                dibujo.setColor(java.awt.Color.WHITE);
                dibujo.fillRect(0, 0, ancho, alto);
                dibujo.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                dibujo.drawImage(original, 0, 0, ancho, alto, null);
            } finally {
                dibujo.dispose();
            }
            // Volvemos a codificar los píxeles para descartar metadatos y contenido ajeno a la
            // foto.
            var salida = new ByteArrayOutputStream();
            ImageIO.write(limpia, "jpeg", salida);
            var imagen = new ImagenProductoEntity();
            imagen.setId(TokensAleatoriosUtil.token(24));
            imagen.setTiendaId(tienda.getId());
            imagen.setContenido(salida.toByteArray());
            imagen.setMime("image/jpeg");
            imagen.setAncho(ancho);
            imagen.setAlto(alto);
            imagen.setCreadaEn(reloj.ahora());
            imagenes.save(imagen);
            limpiarHuerfanas();
            return Map.of(
                    "image",
                    "/api/product-images/" + imagen.getId(),
                    "width",
                    ancho,
                    "height",
                    alto);
        } catch (ReglaNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new ReglaNegocioException(
                    "No se pudo leer la imagen. Prueba con otro JPG o PNG.");
        }
    }

    @Transactional(readOnly = true)
    public ImagenProductoEntity obtener(UsuarioActualPrincipal usuario, String id) {
        var imagen =
                imagenes.findById(id)
                        .orElseThrow(
                                () -> new ReglaNegocioException("No se encontró la imagen.", 404));
        if (!usuario.esPlataforma() && !imagen.getTiendaId().equals(usuario.tiendaId()))
            throw new ReglaNegocioException("No se encontró la imagen.", 404);
        return imagen;
    }

    public void validarReferencia(String imagen, Long tiendaId) {
        if (REFERENCIAS.contains(imagen)) return;
        if (imagen.startsWith("/api/product-images/")) {
            var registro =
                    imagenes.findById(imagen.substring("/api/product-images/".length()))
                            .orElseThrow(
                                    () ->
                                            new ReglaNegocioException(
                                                    "Vuelve a subir la imagen del producto."));
            if (registro.getTiendaId().equals(tiendaId)) return;
        }
        throw new ReglaNegocioException(
                "Sube una foto del producto o selecciona una imagen del catálogo.");
    }

    @Transactional
    public void limpiarHuerfanas() {
        for (var imagen : imagenes.findByCreadaEnBefore(reloj.ahora().minusDays(1))) {
            if (!productos.existsByImagen("/api/product-images/" + imagen.getId()))
                imagenes.delete(imagen);
        }
    }
}
