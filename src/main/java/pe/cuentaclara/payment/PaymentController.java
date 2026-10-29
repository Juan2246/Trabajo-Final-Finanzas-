package pe.cuentaclara.payment;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.auth.CurrentUser;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasAnyRole('PLATFORM', 'MERCHANT')")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping(value = "/preview", consumes = "application/json")
    public PaymentQuote preview(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(name = "store_id", required = false) Long storeId,
            @Valid @RequestBody PaymentRequest request) {
        return payments.preview(user, storeId, request.customerId());
    }

    @PostMapping(consumes = "application/json")
    public PaymentQuote create(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(name = "store_id", required = false) Long storeId,
            @Valid @RequestBody PaymentRequest request) {
        return payments.create(user, storeId, request);
    }
}
