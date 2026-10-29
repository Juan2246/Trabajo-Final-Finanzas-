package pe.cuentaclara.audit;

import org.springframework.stereotype.Service;

import pe.cuentaclara.auth.UserAccountRepository;
import pe.cuentaclara.shared.BusinessTime;

@Service
public class AuditService {
    private final AuditEventRepository events;
    private final UserAccountRepository users;
    private final BusinessTime time;

    public AuditService(
            AuditEventRepository events, UserAccountRepository users, BusinessTime time) {
        this.events = events;
        this.users = users;
        this.time = time;
    }

    public void record(Long userId, Long storeId, String action, Long entityId, String detail) {
        AuditEvent event = new AuditEvent();
        event.setUser(userId == null ? null : users.getReferenceById(userId));
        event.setStoreId(storeId);
        event.setAction(action);
        event.setEntityId(entityId);
        event.setDetail(detail == null ? "" : detail);
        event.setCreatedAt(time.now());
        events.save(event);
    }
}
