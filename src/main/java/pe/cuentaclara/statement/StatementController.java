package pe.cuentaclara.statement;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.auth.CurrentUser;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/statement")
public class StatementController {
    private final StatementService statements;

    public StatementController(StatementService statements) {
        this.statements = statements;
    }

    @GetMapping
    public ObjectNode get(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(name = "store_id", required = false) Long storeId,
            @RequestParam(name = "customer_id", required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate cycle) {
        return statements.get(user, storeId, customerId, date, cycle);
    }
}
