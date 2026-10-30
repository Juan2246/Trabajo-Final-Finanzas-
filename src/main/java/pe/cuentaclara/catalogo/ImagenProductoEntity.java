package pe.cuentaclara.catalogo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_images")
public class ImagenProductoEntity {
    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "store_id")
    private Long tiendaId;

    @Column(name = "content")
    private byte[] contenido;

    @Column(name = "mime")
    private String mime;

    @Column(name = "width")
    private Integer ancho;

    @Column(name = "height")
    private Integer alto;

    @Column(name = "created_at")
    private LocalDateTime creadaEn;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getTiendaId() {
        return tiendaId;
    }

    public void setTiendaId(Long tiendaId) {
        this.tiendaId = tiendaId;
    }

    public byte[] getContenido() {
        return contenido;
    }

    public void setContenido(byte[] contenido) {
        this.contenido = contenido;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public Integer getAncho() {
        return ancho;
    }

    public void setAncho(Integer ancho) {
        this.ancho = ancho;
    }

    public Integer getAlto() {
        return alto;
    }

    public void setAlto(Integer alto) {
        this.alto = alto;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(LocalDateTime creadaEn) {
        this.creadaEn = creadaEn;
    }
}
