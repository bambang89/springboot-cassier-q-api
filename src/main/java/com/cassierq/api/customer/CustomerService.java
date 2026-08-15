package com.cassierq.api.customer;

import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.customer.dto.CustomerRequest;
import com.cassierq.api.customer.dto.CustomerResponse;
import com.cassierq.api.customer.dto.LedgerEntryResponse;
import com.cassierq.api.customer.dto.RecordPaymentRequest;
import com.cassierq.api.domain.entity.Customer;
import com.cassierq.api.domain.entity.CustomerLedgerEntry;
import com.cassierq.api.domain.entity.SalesTransaction;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.repository.CustomerLedgerEntryRepository;
import com.cassierq.api.domain.repository.CustomerRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerLedgerEntryRepository ledgerRepository;
    private final StoreRepository storeRepository;

    @Transactional
    public CustomerResponse create(AppUserPrincipal principal, CustomerRequest request) {
        UUID storeId = requireStore(principal);
        if (customerRepository.existsByStoreIdAndCustomerCodeIgnoreCase(storeId, request.customerCode())) {
            throw new ConflictException("Kode pelanggan sudah dipakai");
        }

        Store store = storeRepository.getReferenceById(storeId);
        Customer customer = customerRepository.save(Customer.builder()
                .store(store)
                .customerCode(request.customerCode())
                .name(request.name())
                .phone(request.phone())
                .address(request.address())
                .creditLimit(request.creditLimit())
                .active(true)
                .build());

        return CustomerResponse.from(customer, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list(AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);
        return customerRepository.findByStoreId(storeId).stream()
                .map(c -> CustomerResponse.from(c, ledgerRepository.balanceOf(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(AppUserPrincipal principal, UUID customerId) {
        Customer customer = requireCustomer(principal, customerId);
        return CustomerResponse.from(customer, ledgerRepository.balanceOf(customerId));
    }

    @Transactional
    public CustomerResponse update(AppUserPrincipal principal, UUID customerId, CustomerRequest request) {
        Customer customer = requireCustomer(principal, customerId);

        if (!customer.getCustomerCode().equalsIgnoreCase(request.customerCode())
                && customerRepository.existsByStoreIdAndCustomerCodeIgnoreCase(customer.getStore().getId(), request.customerCode())) {
            throw new ConflictException("Kode pelanggan sudah dipakai");
        }

        customer.setCustomerCode(request.customerCode());
        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setAddress(request.address());
        customer.setCreditLimit(request.creditLimit());
        customerRepository.save(customer);

        return CustomerResponse.from(customer, ledgerRepository.balanceOf(customerId));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> ledger(AppUserPrincipal principal, UUID customerId) {
        requireCustomer(principal, customerId);
        return ledgerRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }

    @Transactional
    public CustomerResponse recordPayment(AppUserPrincipal principal, UUID customerId, RecordPaymentRequest request, User actor) {
        Customer customer = requireCustomer(principal, customerId);

        ledgerRepository.save(CustomerLedgerEntry.builder()
                .customer(customer)
                .entryType("PAYMENT")
                .amount(request.amount())
                .notes(request.notes())
                .createdBy(actor)
                .createdAt(Instant.now())
                .build());

        return CustomerResponse.from(customer, ledgerRepository.balanceOf(customerId));
    }

    /**
     * Records the unpaid portion of a credit sale as a DEBT entry — called
     * from {@code OrderService} when an order names a customer and pays less
     * than the full total. Rejects if it would push the customer past their
     * {@code creditLimit} (when one is set).
     */
    @Transactional
    public void recordDebtForSale(Customer customer, BigDecimal amount, SalesTransaction transaction, User actor) {
        BigDecimal currentBalance = ledgerRepository.balanceOf(customer.getId());
        BigDecimal newBalance = currentBalance.add(amount);
        if (customer.getCreditLimit() != null && newBalance.compareTo(customer.getCreditLimit()) > 0) {
            throw new BadRequestException("Transaksi ini melebihi limit kredit pelanggan " + customer.getName()
                    + " (limit " + customer.getCreditLimit() + ", akan jadi " + newBalance + ")");
        }

        ledgerRepository.save(CustomerLedgerEntry.builder()
                .customer(customer)
                .entryType("DEBT")
                .amount(amount)
                .salesTransaction(transaction)
                .createdBy(actor)
                .createdAt(Instant.now())
                .build());
    }

    private Customer requireCustomer(AppUserPrincipal principal, UUID customerId) {
        UUID storeId = requireStore(principal);
        return customerRepository.findByIdAndStoreId(customerId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Pelanggan tidak ditemukan"));
    }

    private UUID requireStore(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Akun ini tidak terikat ke toko manapun");
        }
        return storeId;
    }
}
