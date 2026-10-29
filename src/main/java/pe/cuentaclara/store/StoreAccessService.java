package pe.cuentaclara.store;

import org.springframework.stereotype.Service;

import pe.cuentaclara.auth.CurrentUser;
import pe.cuentaclara.customer.Customer;
import pe.cuentaclara.customer.CustomerRepository;
import pe.cuentaclara.shared.BusinessException;

@Service
public class StoreAccessService {
    private final StoreRepository stores;
    private final CustomerRepository customers;

    public StoreAccessService(StoreRepository stores, CustomerRepository customers) {
        this.stores = stores;
        this.customers = customers;
    }

    public Store resolve(CurrentUser user, Long selectedStore, boolean required) {
        Long id = user.isPlatform() ? selectedStore : user.storeId();
        if (id == null) {
            if (required) throw new BusinessException("Primero selecciona una tienda.");
            return null;
        }
        Store store =
                stores.findById(id)
                        .orElseThrow(
                                () -> new BusinessException("La tienda no está disponible.", 403));
        if (!Boolean.TRUE.equals(store.getActive()))
            throw new BusinessException("La tienda no está disponible.", 403);
        return store;
    }

    public Customer customer(Long id, Long storeId) {
        Customer customer =
                customers
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException("No se encontró el cliente.", 404));
        if (!customer.getStore().getId().equals(storeId))
            throw new BusinessException("No se encontró el registro en esta tienda.", 404);
        return customer;
    }

    public void checkCustomer(CurrentUser user, Long customerId) {
        if (user.isCustomer() && !user.customerId().equals(customerId))
            throw new BusinessException("Solo puedes consultar tu cuenta.", 403);
    }
}
