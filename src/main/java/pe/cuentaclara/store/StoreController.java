package pe.cuentaclara.store;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.auth.CurrentUser;

import java.util.Map;

@RestController
@RequestMapping("/api/stores")
@PreAuthorize("hasRole('PLATFORM')")
public class StoreController {
    private final StoreService stores;

    public StoreController(StoreService stores) {
        this.stores = stores;
    }

    @PostMapping(consumes = "application/json")
    public Map<String, Long> create(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody StoreRequest request) {
        return Map.of("id", stores.create(user, request));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json")
    public Map<String, Boolean> update(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id,
            @RequestBody JsonNode patch) {
        stores.update(user, id, patch);
        return Map.of("ok", true);
    }
}
