package pe.cuentaclara.auth;

import org.springframework.stereotype.Service;

import pe.cuentaclara.customer.Customer;
import pe.cuentaclara.shared.BusinessException;
import pe.cuentaclara.store.Store;

@Service
public class AccountService {
    private final UserAccountRepository users;
    private final SessionTokenRepository sessions;
    private final PasswordService passwords;

    public AccountService(
            UserAccountRepository users,
            SessionTokenRepository sessions,
            PasswordService passwords) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
    }

    public UserAccount create(
            String username,
            String name,
            String password,
            String role,
            Store store,
            Customer customer) {
        if (username == null || !username.matches("[A-Za-z0-9_.@-]{3,80}"))
            throw new BusinessException("Usuario: 3–80 letras, números, puntos, @ o guiones.");
        if (name == null || name.isBlank() || name.length() > 120)
            throw new BusinessException("Revisa el nombre del usuario.");
        if (users.findByUsernameIgnoreCase(username).isPresent())
            throw new BusinessException("El usuario ya existe.", 409);
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setName(name.strip());
        user.setPassword(passwords.encode(password));
        user.setRole(role);
        user.setStore(store);
        user.setCustomer(customer);
        user.setActive(true);
        return users.saveAndFlush(user);
    }

    public void updateCustomer(Customer customer, String newPassword) {
        for (UserAccount user : users.findByCustomerId(customer.getId())) {
            user.setName(customer.getName());
            user.setActive(customer.getActive());
            if (!Boolean.TRUE.equals(customer.getActive())
                    || (newPassword != null && !newPassword.isBlank()))
                sessions.deleteByUserId(user.getId());
            if (newPassword != null && !newPassword.isBlank())
                user.setPassword(passwords.encode(newPassword));
        }
    }
}
