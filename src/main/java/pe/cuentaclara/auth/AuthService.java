package pe.cuentaclara.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.audit.AuditService;
import pe.cuentaclara.shared.BusinessException;
import pe.cuentaclara.shared.BusinessTime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final SessionTokenRepository sessions;
    private final LoginChallengeRepository challenges;
    private final LoginAttemptRepository attempts;
    private final PasswordService passwords;
    private final BusinessTime time;
    private final AuditService audit;

    public AuthService(
            UserAccountRepository users,
            SessionTokenRepository sessions,
            LoginChallengeRepository challenges,
            LoginAttemptRepository attempts,
            PasswordService passwords,
            BusinessTime time,
            AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.challenges = challenges;
        this.attempts = attempts;
        this.passwords = passwords;
        this.time = time;
        this.audit = audit;
    }

    @Transactional
    public Map<String, String> challenge() {
        challenges.deleteByExpiresAtLessThan(time.epochSeconds());
        int a = RandomTokens.digit(), b = RandomTokens.digit();
        LoginChallenge challenge = new LoginChallenge();
        challenge.setId(RandomTokens.token(24));
        challenge.setAnswer(Integer.toString(a + b));
        challenge.setExpiresAt(time.epochSeconds() + 300);
        challenges.save(challenge);
        return Map.of("id", challenge.getId(), "question", "¿Cuánto es " + a + " + " + b + "?");
    }

    @Transactional(noRollbackFor = LoginFailedException.class)
    public CurrentUser login(LoginRequest request, String ip) {
        long now = time.epochSeconds();
        String username = request.username().strip();
        List<String> keys =
                List.of("user:" + username.toLowerCase(java.util.Locale.ROOT), "ip:" + ip);
        for (String key : keys) {
            if (attempts.findById(key).map(row -> row.getBlockedUntil() > now).orElse(false))
                throw new BusinessException("Demasiados intentos. Espera 15 minutos.", 429);
        }
        LoginChallenge challenge = challenges.findById(request.challengeId()).orElse(null);
        if (challenge != null) challenges.delete(challenge);
        UserAccount user = users.findByUsernameIgnoreCase(username).orElse(null);
        boolean passwordValid =
                passwords.matches(request.password(), user == null ? "0:0" : user.getPassword());
        boolean challengeValid =
                challenge != null
                        && challenge.getExpiresAt() >= now
                        && MessageDigest.isEqual(
                                challenge.getAnswer().getBytes(StandardCharsets.UTF_8),
                                request.answer().strip().getBytes(StandardCharsets.UTF_8));
        boolean accountValid =
                user != null
                        && Boolean.TRUE.equals(user.getActive())
                        && (user.getStore() == null
                                || Boolean.TRUE.equals(user.getStore().getActive()));
        if (!passwordValid || !challengeValid || !accountValid) {
            for (String key : keys) {
                LoginAttempt attempt = attempts.findById(key).orElseGet(LoginAttempt::new);
                int failures =
                        attempt.getUpdatedAt() != null && attempt.getUpdatedAt() > now - 900
                                ? attempt.getFailures() + 1
                                : 1;
                attempt.setKey(key);
                attempt.setFailures(failures);
                attempt.setUpdatedAt(now);
                attempt.setBlockedUntil(
                        failures >= (key.startsWith("ip:") ? 20 : 5) ? now + 900 : 0L);
                attempts.save(attempt);
            }
            throw new LoginFailedException();
        }
        attempts.findById(keys.getFirst()).ifPresent(attempts::delete);
        if (passwords.needsUpgrade(user.getPassword()))
            user.setPassword(passwords.encode(request.password()));
        sessions.deleteByExpiresAtLessThan(now);
        SessionToken session = new SessionToken();
        session.setToken(RandomTokens.token(48));
        session.setCsrf(RandomTokens.token(32));
        session.setUser(user);
        session.setExpiresAt(now + 28800);
        sessions.save(session);
        Long storeId = user.getStore() == null ? null : user.getStore().getId();
        audit.record(user.getId(), storeId, "Inicio de sesión", null, "");
        return new CurrentUser(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getRole(),
                storeId,
                user.getCustomer() == null ? null : user.getCustomer().getId(),
                session.getToken(),
                session.getCsrf());
    }

    @Transactional
    public void logout(CurrentUser user) {
        audit.record(user.id(), user.storeId(), "Cierre de sesión", null, "");
        sessions.deleteById(user.sessionToken());
    }

    @Transactional
    public void changePassword(CurrentUser actor, PasswordRequest request) {
        UserAccount user = users.findById(actor.id()).orElseThrow();
        if (!passwords.matches(request.currentPassword(), user.getPassword()))
            throw new BusinessException("La contraseña actual no coincide.");
        user.setPassword(passwords.encode(request.password()));
        sessions.findAllByUserId(user.getId()).stream()
                .filter(session -> !session.getToken().equals(actor.sessionToken()))
                .forEach(sessions::delete);
        audit.record(user.getId(), actor.storeId(), "Contraseña actualizada", null, "");
    }
}
