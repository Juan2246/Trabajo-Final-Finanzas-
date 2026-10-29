package pe.cuentaclara.catalog;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        Long storeId,
        String name,
        String description,
        String brand,
        String supplier,
        String unit,
        String image,
        BigDecimal cashPrice,
        BigDecimal creditPrice,
        String currency,
        Boolean installmentAllowed,
        Boolean active) {
    public static ProductResponse from(Product row) {
        return new ProductResponse(
                row.getId(),
                row.getStore() == null ? null : row.getStore().getId(),
                row.getName(),
                row.getDescription(),
                row.getBrand(),
                row.getSupplier(),
                row.getUnit(),
                row.getImage(),
                row.getCashPrice(),
                row.getCreditPrice(),
                row.getCurrency(),
                row.getInstallmentAllowed(),
                row.getActive());
    }
}
