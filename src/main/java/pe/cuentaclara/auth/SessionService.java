package pe.cuentaclara.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.shared.BusinessTime;

import java.util.Optional;

@Service
public class SessionService {
    private final SessionTokenRepository sessions;
    private final BusinessTime time;

    public SessionService(SessionTokenRepository sessions, BusinessTime time) {
        this.sessions = sessions;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public Optional<CurrentUser> lookup(String token) {
        if (token == null || token.length() > 128) return Optional.empty();
        return sessions.findById(token)
                .filter(session -> session.getExpiresAt() > time.epochSeconds())
                .filter(session -> Boolean.TRUE.equals(session.getUser().getActive()))
                .filter(
                        session ->
                                session.getUser().getStore() == null
                                        || Boolean.TRUE.equals(
                                                session.getUser().getStore().getActive()))
                .map(
                        session -> {
                            UserAccount user = session.getUser();
                            return new CurrentUser(
                                    user.getId(),
                                    user.getUsername(),
                                    user.getName(),
                                    user.getRole(),
                                    user.getStore() == null ? null : user.getStore().getId(),
                                    user.getCustomer() == null ? null : user.getCustomer().getId(),
                                    session.getToken(),
                                    session.getCsrf());
                        });
    }
}
