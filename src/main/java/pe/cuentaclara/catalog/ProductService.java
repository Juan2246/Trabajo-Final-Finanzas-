package pe.cuentaclara.catalog;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.audit.AuditService;
import pe.cuentaclara.auth.CurrentUser;
import pe.cuentaclara.shared.BusinessException;
import pe.cuentaclara.shared.JsonSupport;
import pe.cuentaclara.store.StoreAccessService;

import java.net.URI;

@Service
public class ProductService {
    private final ProductRepository products;
    private final StoreAccessService access;
    private final AuditService audit;
    private final JsonSupport json;

    public ProductService(
            ProductRepository products,
            StoreAccessService access,
            AuditService audit,
            JsonSupport json) {
        this.products = products;
        this.access = access;
        this.audit = audit;
        this.json = json;
    }

    @Transactional
    public Long create(CurrentUser actor, Long selectedStore, ProductRequest request) {
        var store = access.resolve(actor, selectedStore, true);
        Product product = new Product();
        product.setStore(store);
        apply(product, request);
        products.saveAndFlush(product);
        audit.record(actor.id(), store.getId(), "Producto registrado", product.getId(), "");
        return product.getId();
    }

    @Transactional
    public void update(CurrentUser actor, Long selectedStore, Long id, JsonNode patch) {
        var store = access.resolve(actor, selectedStore, true);
        Product product = find(id, store.getId());
        apply(product, json.merge(ProductResponse.from(product), patch, ProductRequest.class));
        audit.record(actor.id(), store.getId(), "Producto actualizado", id, "");
    }

    public Product find(Long id, Long storeId) {
        Product product =
                products.findById(id)
                        .orElseThrow(
                                () -> new BusinessException("No se encontró el producto.", 404));
        if (!product.getStore().getId().equals(storeId))
            throw new BusinessException("No se encontró el registro en esta tienda.", 404);
        return product;
    }

    private void apply(Product product, ProductRequest request) {
        String image = clean(request.image());
        if (!image.isEmpty()) {
            URI uri = URI.create(image);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null)
                throw new BusinessException("La imagen debe ser un enlace HTTPS.");
        }
        product.setName(request.name().strip());
        product.setDescription(clean(request.description()));
        product.setBrand(clean(request.brand()));
        product.setSupplier(clean(request.supplier()));
        product.setUnit(request.unit().strip());
        product.setImage(image);
        product.setCashPrice(request.cashPrice());
        product.setCreditPrice(request.creditPrice());
        product.setCurrency(request.currency());
        product.setInstallmentAllowed(
                request.installmentAllowed() == null || request.installmentAllowed());
        product.setActive(request.active() == null || request.active());
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
