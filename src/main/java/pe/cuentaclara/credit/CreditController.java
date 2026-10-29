package pe.cuentaclara.credit;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.auth.CurrentUser;

@RestController
@RequestMapping("/api/sales")
@PreAuthorize("hasAnyRole('PLATFORM', 'MERCHANT')")
public class CreditController {
    private final CreditService credits;

    public CreditController(CreditService credits) {
        this.credits = credits;
    }

    @PostMapping(value = "/preview", consumes = "application/json")
    public SalePreview preview(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(name = "store_id", required = false) Long storeId,
            @Valid @RequestBody SaleRequest request) {
        return credits.preview(user, storeId, request);
    }

    @PostMapping(consumes = "application/json")
    public SalePreview create(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(name = "store_id", required = false) Long storeId,
            @Valid @RequestBody SaleRequest request) {
        return credits.create(user, storeId, request);
    }
}
