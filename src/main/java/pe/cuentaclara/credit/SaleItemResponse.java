package pe.cuentaclara.credit;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record SaleItemResponse(
        Long id,
        Long saleId,
        Long productId,
        String name,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {
    public static SaleItemResponse from(SaleItem row) {
        return new SaleItemResponse(
                row.getId(),
                row.getSale() == null ? null : row.getSale().getId(),
                row.getProduct() == null ? null : row.getProduct().getId(),
                row.getName(),
                row.getQuantity(),
                row.getUnitPrice(),
                row.getSubtotal());
    }
}
