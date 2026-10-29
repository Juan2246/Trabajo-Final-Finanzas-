package pe.cuentaclara.store;

import jakarta.validation.constraints.*;

public record StoreResponse(Long id, String name, String business, String address, Boolean active) {
    public static StoreResponse from(Store row) {
        return new StoreResponse(
                row.getId(), row.getName(), row.getBusiness(), row.getAddress(), row.getActive());
    }
}
