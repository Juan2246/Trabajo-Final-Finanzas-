package pe.cuentaclara.customer;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.auth.CurrentUser;

import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@PreAuthorize("hasAnyRole('PLATFORM', 'MERCHANT')")
public class CustomerController {
    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @PostMapping(consumes = "application/json")
    public Map<String, Long> create(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(name = "store_id", required = false) Long storeId,
            @Valid @RequestBody CustomerRequest request) {
        return Map.of("id", customers.create(user, storeId, request));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json")
    public Map<String, Boolean> update(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(name = "store_id", required = false) Long storeId,
            @PathVariable Long id,
            @RequestBody JsonNode patch) {
        customers.update(user, storeId, id, patch);
        return Map.of("ok", true);
    }
}
