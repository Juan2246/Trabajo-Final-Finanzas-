package pe.cuentaclara.store;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.audit.AuditService;
import pe.cuentaclara.auth.AccountService;
import pe.cuentaclara.auth.CurrentUser;
import pe.cuentaclara.auth.SessionTokenRepository;
import pe.cuentaclara.shared.BusinessException;
import pe.cuentaclara.shared.JsonSupport;

@Service
public class StoreService {
    private final StoreRepository stores;
    private final AccountService accounts;
    private final SessionTokenRepository sessions;
    private final AuditService audit;
    private final JsonSupport json;

    public StoreService(
            StoreRepository stores,
            AccountService accounts,
            SessionTokenRepository sessions,
            AuditService audit,
            JsonSupport json) {
        this.stores = stores;
        this.accounts = accounts;
        this.sessions = sessions;
        this.audit = audit;
        this.json = json;
    }

    @Transactional
    public Long create(CurrentUser actor, StoreRequest request) {
        Store store = new Store();
        apply(store, request);
        stores.saveAndFlush(store);
        accounts.create(
                request.username(),
                request.adminName(),
                request.password(),
                "merchant",
                store,
                null);
        audit.record(actor.id(), store.getId(), "Tienda creada", store.getId(), store.getName());
        return store.getId();
    }

    @Transactional
    public void update(CurrentUser actor, Long id, JsonNode patch) {
        Store store =
                stores.findById(id)
                        .orElseThrow(() -> new BusinessException("Tienda no encontrada.", 404));
        StoreRequest request = json.merge(StoreResponse.from(store), patch, StoreRequest.class);
        apply(store, request);
        if (!store.getActive()) sessions.deleteByUserStoreId(id);
        audit.record(actor.id(), id, "Tienda actualizada", id, "");
    }

    private void apply(Store store, StoreRequest request) {
        store.setName(request.name().strip());
        store.setBusiness(request.business().strip());
        store.setAddress(request.address() == null ? "" : request.address().strip());
        store.setActive(request.active() == null || request.active());
    }
}
