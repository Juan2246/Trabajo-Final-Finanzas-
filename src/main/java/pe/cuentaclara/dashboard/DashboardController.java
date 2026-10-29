package pe.cuentaclara.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.auth.CurrentUser;

@RestController
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/data")
    public DashboardResponse data(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(name = "store_id", required = false) Long storeId) {
        return dashboard.get(user, storeId);
    }
}
