package com.cassierq.api.sales;

import com.cassierq.api.catalog.CatalogScope;
import com.cassierq.api.common.NumberSequenceService;
import com.cassierq.api.common.PageResponse;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.config.BusinessProperties;
import com.cassierq.api.customer.CustomerService;
import com.cassierq.api.cashier.CashierSessionService;
import com.cassierq.api.promo.PromoCodeService;
import com.cassierq.api.settings.CheckoutSettingsService;
import com.cassierq.api.domain.entity.CashierSession;
import com.cassierq.api.domain.entity.Customer;
import com.cassierq.api.domain.entity.CustomerLedgerEntry;
import com.cassierq.api.domain.entity.Inventory;
import com.cassierq.api.domain.entity.Payment;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.entity.ProductUnitConversion;
import com.cassierq.api.domain.entity.SalesTransaction;
import com.cassierq.api.domain.entity.SalesTransactionItem;
import com.cassierq.api.domain.entity.StockMovement;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.Unit;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.repository.CustomerLedgerEntryRepository;
import com.cassierq.api.domain.repository.CustomerRepository;
import com.cassierq.api.domain.repository.InventoryRepository;
import com.cassierq.api.domain.repository.PaymentRepository;
import com.cassierq.api.domain.repository.ProductPriceRepository;
import com.cassierq.api.domain.repository.ProductUnitConversionRepository;
import com.cassierq.api.domain.repository.SalesTransactionItemRepository;
import com.cassierq.api.domain.repository.SalesTransactionRepository;
import com.cassierq.api.domain.repository.StockMovementRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.sales.dto.CreateOrderItemRequest;
import com.cassierq.api.sales.dto.CreateOrderRequest;
import com.cassierq.api.sales.dto.OrderItemResponse;
import com.cassierq.api.sales.dto.OrderResponse;
import com.cassierq.api.sales.dto.ReceiptItemResponse;
import com.cassierq.api.sales.dto.ReceiptResponse;
import com.cassierq.api.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    // Pembulatan PPN di HP (Math.round) vs di sini boleh beda 1 rupiah.
    private static final BigDecimal TAX_TOLERANCE = BigDecimal.ONE;

    private final CatalogScope catalogScope;
    private final ProductUnitConversionRepository conversionRepository;
    private final ProductPriceRepository priceRepository;
    private final InventoryRepository inventoryRepository;
    private final CashierSessionService cashierSessionService;
    private final CheckoutSettingsService checkoutSettingsService;
    private final PromoCodeService promoCodeService;
    private final com.cassierq.api.catalog.bundle.BundleComposition bundleComposition;
    private final com.cassierq.api.catalog.pricing.PriceTierService priceTierService;
    private final com.cassierq.api.access.ActionAccessService actionAccess;
    private final NumberSequenceService numberSequenceService;
    private final SalesTransactionRepository transactionRepository;
    private final SalesTransactionItemRepository itemRepository;
    private final PaymentRepository paymentRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final CustomerLedgerEntryRepository ledgerRepository;
    private final BusinessProperties businessProperties;

    /**
     * @param idempotencyKey opsional; kalau transaksi dengan key yang sama di toko ini
     *                       sudah ada (retry setelah timeout), transaksi itu yang dikembalikan
     *                       — stok tidak dipotong dua kali.
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest request, AppUserPrincipal principal, String idempotencyKey) {
        UUID storeId = requireStore(principal);
        if (idempotencyKey != null) {
            Optional<OrderResponse> existing = findByIdempotencyKey(storeId, idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Toko tidak ditemukan"));
        CashierSession session = cashierSessionService.findActiveSession(principal.getUserId())
                .orElseThrow(() -> new BadRequestException("Anda belum membuka sesi kasir"));
        if (!checkoutSettingsService.enabledPaymentMethods(storeId).contains(request.paymentMethod())) {
            throw new BadRequestException("Metode pembayaran ini tidak diaktifkan di toko");
        }
        User cashier = userRepository.getReferenceById(principal.getUserId());
        UUID catalogOwner = catalogScope.ownerOf(principal);

        BigDecimal discountAmount = request.discountAmount() != null ? request.discountAmount() : BigDecimal.ZERO;
        BigDecimal taxAmount = request.taxAmount() != null ? request.taxAmount() : BigDecimal.ZERO;

        // Kunci baris stok dalam urutan productId yang tetap: dua kasir dengan
        // keranjang [A,B] dan [B,A] tidak saling menunggu (deadlock). Lookup
        // berikutnya di priceAndReserveLine memakai entity yang sudah terkunci.
        lockInventoryInOrder(storeId, stockProductIds(request.items(), catalogOwner));

        List<SalesTransactionItem> items = new ArrayList<>();
        List<StockMovement> movements = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        // SalesTransaction row must exist before items/movements can reference
        // it, but we need the computed totals first — build it once totals
        // are known, right before saving everything together at the end.
        // Wholesale steps depend on the product's total in this sale, across all its units.
        Map<UUID, BigDecimal> baseTotals = baseQuantityPerProduct(request.items(), catalogOwner);
        for (CreateOrderItemRequest itemRequest : request.items()) {
            LineResult line = priceAndReserveLine(storeId, catalogOwner, itemRequest, baseTotals);
            subtotal = subtotal.add(line.subtotal);
            items.add(line.item);
            movements.addAll(line.movements);
        }

        // Manual discount and promo codes each need their own permission (Hak Akses Menu → Kasir).
        if (discountAmount.signum() > 0 && !actionAccess.allowed(principal, "pos.discount")) {
            throw new BadRequestException("Anda tidak diizinkan memberi diskon manual");
        }
        if (request.promoCode() != null && !request.promoCode().isBlank() && !actionAccess.allowed(principal, "pos.promo")) {
            throw new BadRequestException("Anda tidak diizinkan memakai kode promo");
        }
        // The cashier discount limit applies to the manual discount only; a promo code
        // is approved by the owner, so its discount is added after that check.
        validateDiscount(principal, subtotal, discountAmount);
        PromoCodeService.Redemption promo = null;
        if (request.promoCode() != null && !request.promoCode().isBlank()) {
            promo = promoCodeService.redeem(storeId, request.promoCode(), subtotal.subtract(discountAmount).max(BigDecimal.ZERO));
            discountAmount = discountAmount.add(promo.discount());
        }
        BigDecimal afterDiscount = subtotal.subtract(discountAmount);
        if (afterDiscount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Diskon melebihi total belanja");
        }
        if (!checkoutSettingsService.taxEnabled(storeId) && taxAmount.signum() != 0) {
            throw new BadRequestException("PPN tidak diaktifkan di toko ini");
        }
        validateTax(afterDiscount, taxAmount);
        BigDecimal grandTotal = afterDiscount.add(taxAmount);

        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findByIdAndStoreId(request.customerId(), storeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Pelanggan tidak ditemukan"));
        }

        BigDecimal debtAmount = BigDecimal.ZERO;
        BigDecimal changeAmount;
        boolean shortPaid = request.paymentAmount().compareTo(grandTotal) < 0;
        if (customer != null && shortPaid) {
            // Credit sale — the unpaid part becomes a debt for this customer
            // instead of rejecting the order (checked further down, after
            // the transaction exists, since recordDebtForSale needs its id).
            debtAmount = grandTotal.subtract(request.paymentAmount());
            changeAmount = BigDecimal.ZERO;
        } else if (shortPaid) {
            throw new BadRequestException("Jumlah pembayaran kurang dari total belanja");
        } else {
            changeAmount = request.paymentAmount().subtract(grandTotal);
        }

        String transactionNumber = numberSequenceService.next(store, "SALES_TRANSACTION");
        Instant now = Instant.now();

        SalesTransaction transaction = transactionRepository.save(SalesTransaction.builder()
                .transactionNumber(transactionNumber)
                .store(store)
                .cashier(cashier)
                .cashierSession(session)
                .transactionDate(now)
                .subtotal(subtotal)
                .discountAmount(discountAmount)
                .taxAmount(taxAmount)
                .grandTotal(grandTotal)
                .paymentAmount(request.paymentAmount())
                .changeAmount(changeAmount)
                .transactionStatus("PAID")
                .idempotencyKey(idempotencyKey)
                .promoCode(promo != null ? promo.promo() : null)
                .promoCodeText(promo != null ? promo.promo().getCode() : null)
                .promoDiscountAmount(promo != null ? promo.discount() : null)
                .build());

        items.forEach(item -> {
            item.setTransaction(transaction);
            item.setCreatedAt(now);
        });
        itemRepository.saveAll(items);

        movements.forEach(movement -> {
            movement.setReferenceType("SALES_TRANSACTION");
            movement.setReferenceId(transaction.getId());
            movement.setCreatedBy(cashier);
            movement.setCreatedAt(now);
        });
        stockMovementRepository.saveAll(movements);

        // payments.amount harus > 0 (CK_payments_amount_positive) — penjualan
        // kredit penuh tanpa uang muka tidak punya baris payment.
        if (request.paymentAmount().compareTo(BigDecimal.ZERO) > 0) {
            paymentRepository.save(Payment.builder()
                    .transaction(transaction)
                    .paymentMethod(request.paymentMethod())
                    .amount(request.paymentAmount())
                    .paymentStatus("SUCCESS")
                    .paidAt(now)
                    .createdBy(cashier)
                    .createdAt(now)
                    .build());
        }

        if (customer != null && debtAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Throws (rolling back the whole sale, stock deduction included)
            // if this would push the customer past their credit limit.
            customerService.recordDebtForSale(customer, debtAmount, transaction, cashier);
        }

        return OrderResponse.from(transaction, items.stream().map(OrderItemResponse::from).toList(),
                customer != null ? customer.getId() : null, customer != null ? debtAmount : null);
    }

    /** Dipakai juga oleh controller saat dua retry yang sama balapan dan salah satunya kena unique index. */
    @Transactional(readOnly = true)
    public Optional<OrderResponse> findByIdempotencyKey(UUID storeId, String idempotencyKey) {
        return transactionRepository.findByStoreIdAndIdempotencyKey(storeId, idempotencyKey)
                .map(t -> {
                    var debt = ledgerRepository.findFirstBySalesTransactionIdAndEntryType(t.getId(), "DEBT");
                    return OrderResponse.from(t,
                            itemRepository.findByTransactionId(t.getId()).stream().map(OrderItemResponse::from).toList(),
                            debt.map(e -> e.getCustomer().getId()).orElse(null),
                            debt.map(CustomerLedgerEntry::getAmount).orElse(null));
                });
    }

    @Transactional
    public OrderResponse voidOrder(UUID id, AppUserPrincipal principal, String reason) {
        UUID storeId = requireStore(principal);
        SalesTransaction transaction = transactionRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaksi tidak ditemukan"));

        if (!"PAID".equals(transaction.getTransactionStatus())) {
            throw new BadRequestException("Hanya transaksi berstatus PAID yang bisa dibatalkan");
        }
        if (transaction.getPromoCode() != null) {
            // A voided sale gives its promo use back.
            promoCodeService.release(transaction.getPromoCode().getId());
        }

        User voider = userRepository.getReferenceById(principal.getUserId());
        Instant now = Instant.now();
        List<SalesTransactionItem> items = itemRepository.findByTransactionId(id);
        // Give back exactly the stock the sale took, as recorded in its SALE movements —
        // for a bundle these are its items, even if the bundle's contents changed since.
        List<StockMovement> taken = stockMovementRepository
                .findByReferenceTypeAndReferenceIdAndMovementType("SALES_TRANSACTION", id, "SALE");
        lockInventoryInOrder(storeId, taken.stream().map(m -> m.getProduct().getId()).toList());

        for (StockMovement sale : taken) {
            Inventory inventory = inventoryRepository.findForUpdate(storeId, sale.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Stok produk tidak ditemukan"));
            BigDecimal before = inventory.getQuantityBaseUnit();
            BigDecimal after = before.add(sale.getQuantityBaseUnit());
            inventory.setQuantityBaseUnit(after);
            inventory.setUpdatedAt(now);
            inventoryRepository.save(inventory);

            stockMovementRepository.save(StockMovement.builder()
                    .store(transaction.getStore())
                    .product(sale.getProduct())
                    .movementType("VOID_TRANSACTION")
                    .quantity(sale.getQuantity())
                    .unit(sale.getUnit())
                    .quantityBaseUnit(sale.getQuantityBaseUnit())
                    .stockBefore(before)
                    .stockAfter(after)
                    .referenceType("SALES_TRANSACTION")
                    .referenceId(transaction.getId())
                    .notes(sale.getNotes())
                    .createdBy(voider)
                    .createdAt(now)
                    .build());
        }

        // Uang dikembalikan: payment tidak lagi dihitung di expected cash sesi kasir.
        paymentRepository.findByTransactionId(id).forEach(payment -> {
            payment.setPaymentStatus("REFUNDED");
            paymentRepository.save(payment);
        });

        // Penjualan kredit: utangnya ikut batal (entry VOID, ledger tetap append-only).
        ledgerRepository.findFirstBySalesTransactionIdAndEntryType(id, "DEBT").ifPresent(debt ->
                ledgerRepository.save(CustomerLedgerEntry.builder()
                        .customer(debt.getCustomer())
                        .entryType("VOID")
                        .amount(debt.getAmount())
                        .salesTransaction(transaction)
                        .notes("Void " + transaction.getTransactionNumber() + ": " + reason)
                        .createdBy(voider)
                        .createdAt(now)
                        .build()));

        transaction.setTransactionStatus("VOID");
        transaction.setVoidReason(reason);
        transaction.setVoidedBy(voider);
        transaction.setVoidedAt(now);
        transactionRepository.save(transaction);

        return toResponse(transaction, items);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(AppUserPrincipal principal, Pageable pageable) {
        UUID storeId = requireStore(principal);
        Page<SalesTransaction> page = transactionRepository.findByStoreIdOrderByTransactionDateDesc(storeId, pageable);
        // Satu query untuk item semua transaksi di halaman ini, bukan satu per transaksi.
        List<UUID> ids = page.getContent().stream().map(SalesTransaction::getId).toList();
        Map<UUID, List<SalesTransactionItem>> itemsByTransaction = ids.isEmpty()
                ? Map.of()
                : itemRepository.findByTransactionIdIn(ids).stream()
                        .collect(Collectors.groupingBy(item -> item.getTransaction().getId()));
        return PageResponse.of(page.map(t -> toResponse(t, itemsByTransaction.getOrDefault(t.getId(), List.of()))));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);
        SalesTransaction transaction = transactionRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaksi tidak ditemukan"));
        return toResponse(transaction, itemRepository.findByTransactionId(id));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(UUID id, AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);
        SalesTransaction transaction = transactionRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaksi tidak ditemukan"));
        List<SalesTransactionItem> items = itemRepository.findByTransactionId(id);

        String paymentMethod = paymentRepository.findByTransactionId(id).stream()
                .findFirst()
                .map(Payment::getPaymentMethod)
                .orElse(null);

        String customerName = null;
        BigDecimal debtAmount = null;
        var debtEntry = ledgerRepository.findFirstBySalesTransactionIdAndEntryType(id, "DEBT");
        if (debtEntry.isPresent()) {
            customerName = debtEntry.get().getCustomer().getName();
            debtAmount = debtEntry.get().getAmount();
        }

        Store store = transaction.getStore();
        return new ReceiptResponse(
                transaction.getId(),
                transaction.getTransactionNumber(),
                transaction.getTransactionDate(),
                transaction.getTransactionStatus(),
                store.getStoreName(),
                store.getAddress(),
                store.getPhone(),
                transaction.getCashier().getEmployee().getFullName(),
                items.stream().map(ReceiptItemResponse::from).toList(),
                transaction.getSubtotal(),
                transaction.getDiscountAmount(),
                transaction.getTaxAmount(),
                transaction.getGrandTotal(),
                paymentMethod,
                transaction.getPaymentAmount(),
                transaction.getChangeAmount(),
                customerName,
                debtAmount);
    }

    private void lockInventoryInOrder(UUID storeId, List<UUID> productIds) {
        productIds.stream()
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(productId -> inventoryRepository.findForUpdate(storeId, productId));
    }

    /** Diskon di atas batas hanya boleh diberikan SUPERADMIN / KEPALA_TOKO. */
    private void validateDiscount(AppUserPrincipal principal, BigDecimal subtotal, BigDecimal discountAmount) {
        if (discountAmount.signum() == 0 || principal.hasRole("SUPERADMIN") || principal.hasRole("KEPALA_TOKO")) {
            return;
        }
        BigDecimal maxPercent = businessProperties.cashierMaxDiscountPercent();
        BigDecimal maxDiscount = subtotal.multiply(maxPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        if (discountAmount.compareTo(maxDiscount) > 0) {
            throw new BadRequestException("Diskon maksimal untuk kasir " + maxPercent.stripTrailingZeros().toPlainString()
                    + "% — minta Kepala Toko untuk diskon lebih besar");
        }
    }

    /** PPN dari HP harus 0 (tanpa PPN) atau sama dengan tarif PPN × (subtotal − diskon). */
    private void validateTax(BigDecimal taxableAmount, BigDecimal taxAmount) {
        if (taxAmount.signum() == 0) {
            return;
        }
        BigDecimal expected = taxableAmount.multiply(businessProperties.taxRate()).setScale(0, RoundingMode.HALF_UP);
        if (taxAmount.subtract(expected).abs().compareTo(TAX_TOLERANCE) > 0) {
            throw new BadRequestException("Nilai PPN tidak sesuai (seharusnya " + expected.toPlainString() + ")");
        }
    }

    private LineResult priceAndReserveLine(
            UUID storeId, UUID catalogOwner, CreateOrderItemRequest itemRequest, Map<UUID, BigDecimal> baseTotals) {
        Product product = catalogScope.product(itemRequest.productId(), catalogOwner);
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new BadRequestException("Produk " + product.getProductName() + " tidak aktif");
        }
        Unit unit = catalogScope.unit(itemRequest.unitId(), catalogOwner);
        ProductUnitConversion conversion = conversionRepository.findByProductIdAndUnitId(product.getId(), unit.getId())
                .orElseThrow(() -> new BadRequestException("Satuan tersebut tidak berlaku untuk produk " + product.getProductName()));
        var price = priceRepository.findByStoreIdAndProductIdAndEffectiveUntilIsNull(storeId, product.getId())
                .orElseThrow(() -> new BadRequestException("Produk " + product.getProductName() + " belum punya harga di toko ini"));

        BigDecimal quantityBaseUnit = itemRequest.quantity().multiply(conversion.getConversionToBase());
        // Selling price is per base unit: a dus of 12 costs 12 × that price (was charged as 1×),
        // lowered to the wholesale step the product's total in this sale reaches.
        BigDecimal basePrice = product.isBundle()
                ? price.getSellingPrice()
                : priceTierService.basePriceFor(storeId, product.getId(),
                        baseTotals.getOrDefault(product.getId(), quantityBaseUnit), price.getSellingPrice());
        BigDecimal unitPrice = basePrice.multiply(conversion.getConversionToBase());
        BigDecimal lineSubtotal = unitPrice.multiply(itemRequest.quantity());

        SalesTransactionItem item = SalesTransactionItem.builder()
                .product(product)
                .quantity(itemRequest.quantity())
                .unit(unit)
                .quantityBaseUnit(quantityBaseUnit)
                .unitPrice(unitPrice)
                .discount(BigDecimal.ZERO)
                .subtotal(lineSubtotal)
                .build();

        List<StockMovement> movements = new ArrayList<>();
        if (product.isBundle()) {
            // A bundle has no stock of its own: take each item's share from that item's stock.
            var bundleItems = bundleComposition.items(product.getId());
            if (bundleItems.isEmpty()) {
                throw new BadRequestException("Paket " + product.getProductName() + " belum punya isi");
            }
            for (var bundleItem : bundleItems) {
                Product component = bundleItem.getComponent();
                if (!"ACTIVE".equals(component.getStatus()) || component.getDeletedAt() != null) {
                    throw new BadRequestException("Isi paket " + component.getProductName() + " sudah tidak aktif");
                }
                BigDecimal componentBase = bundleComposition.baseQuantityPerBundle(bundleItem).multiply(quantityBaseUnit);
                movements.add(takeStock(storeId, component, bundleItem.getQuantity().multiply(quantityBaseUnit),
                        bundleItem.getUnit(), componentBase, "Paket " + product.getProductName()));
            }
        } else {
            movements.add(takeStock(storeId, product, itemRequest.quantity(), unit, quantityBaseUnit, null));
        }

        return new LineResult(item, movements, lineSubtotal);
    }

    private OrderResponse toResponse(SalesTransaction transaction, List<SalesTransactionItem> items) {
        return OrderResponse.from(transaction, items.stream().map(OrderItemResponse::from).toList());
    }

    private UUID requireStore(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Akun ini tidak terikat ke toko manapun");
        }
        return storeId;
    }

    /** Deducts stock for a sale and returns the SALE movement (reference is set once the sale is saved). */
    private StockMovement takeStock(UUID storeId, Product product, BigDecimal quantity, Unit unit,
            BigDecimal quantityBaseUnit, String notes) {
        Inventory inventory = inventoryRepository.findForUpdate(storeId, product.getId())
                .orElseThrow(() -> new BadRequestException("Stok produk " + product.getProductName() + " belum diatur di toko ini"));
        BigDecimal before = inventory.getQuantityBaseUnit();
        if (before.compareTo(quantityBaseUnit) < 0) {
            throw new BadRequestException("Stok " + product.getProductName() + " tidak cukup");
        }
        BigDecimal after = before.subtract(quantityBaseUnit);
        inventory.setQuantityBaseUnit(after);
        inventory.setUpdatedAt(Instant.now());
        inventoryRepository.save(inventory);

        return StockMovement.builder()
                .store(inventory.getStore())
                .product(product)
                .movementType("SALE")
                .quantity(quantity)
                .unit(unit)
                .quantityBaseUnit(quantityBaseUnit)
                .stockBefore(before)
                .stockAfter(after)
                .notes(notes)
                .build();
    }

    /** Total base-unit quantity of each (non-bundle) product across all lines of a sale. */
    private Map<UUID, BigDecimal> baseQuantityPerProduct(List<CreateOrderItemRequest> lines, UUID catalogOwner) {
        Map<UUID, BigDecimal> totals = new java.util.HashMap<>();
        for (CreateOrderItemRequest line : lines) {
            conversionRepository.findByProductIdAndUnitId(line.productId(), line.unitId()).ifPresent(conversion ->
                    totals.merge(line.productId(), line.quantity().multiply(conversion.getConversionToBase()), BigDecimal::add));
        }
        return totals;
    }

    /** Products whose stock a sale touches: the product itself, or every item of a bundle. */
    private List<UUID> stockProductIds(List<CreateOrderItemRequest> lines, UUID catalogOwner) {
        List<UUID> ids = new ArrayList<>();
        for (CreateOrderItemRequest line : lines) {
            Product product = catalogScope.product(line.productId(), catalogOwner);
            if (product.isBundle()) {
                bundleComposition.items(product.getId()).forEach(item -> ids.add(item.getComponent().getId()));
            } else {
                ids.add(product.getId());
            }
        }
        return ids;
    }

    private record LineResult(SalesTransactionItem item, List<StockMovement> movements, BigDecimal subtotal) {
    }
}
