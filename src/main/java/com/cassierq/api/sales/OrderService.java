package com.cassierq.api.sales;

import com.cassierq.api.common.PageResponse;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.CashierSession;
import com.cassierq.api.domain.entity.Inventory;
import com.cassierq.api.domain.entity.NumberSequence;
import com.cassierq.api.domain.entity.Payment;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.entity.ProductUnitConversion;
import com.cassierq.api.domain.entity.SalesTransaction;
import com.cassierq.api.domain.entity.SalesTransactionItem;
import com.cassierq.api.domain.entity.StockMovement;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.Unit;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.repository.CashierSessionRepository;
import com.cassierq.api.domain.repository.InventoryRepository;
import com.cassierq.api.domain.repository.NumberSequenceRepository;
import com.cassierq.api.domain.repository.PaymentRepository;
import com.cassierq.api.domain.repository.ProductPriceRepository;
import com.cassierq.api.domain.repository.ProductRepository;
import com.cassierq.api.domain.repository.ProductUnitConversionRepository;
import com.cassierq.api.domain.repository.SalesTransactionItemRepository;
import com.cassierq.api.domain.repository.SalesTransactionRepository;
import com.cassierq.api.domain.repository.StockMovementRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.UnitRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.sales.dto.CreateOrderItemRequest;
import com.cassierq.api.sales.dto.CreateOrderRequest;
import com.cassierq.api.sales.dto.OrderItemResponse;
import com.cassierq.api.sales.dto.OrderResponse;
import com.cassierq.api.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter SEQUENCE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);

    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final ProductUnitConversionRepository conversionRepository;
    private final ProductPriceRepository priceRepository;
    private final InventoryRepository inventoryRepository;
    private final CashierSessionRepository cashierSessionRepository;
    private final NumberSequenceRepository numberSequenceRepository;
    private final SalesTransactionRepository transactionRepository;
    private final SalesTransactionItemRepository itemRepository;
    private final PaymentRepository paymentRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    @Transactional
    public OrderResponse create(CreateOrderRequest request, AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Toko tidak ditemukan"));
        CashierSession session = cashierSessionRepository.findByCashierIdAndStatus(principal.getUserId(), "OPEN")
                .orElseThrow(() -> new BadRequestException("Anda belum membuka sesi kasir"));
        User cashier = userRepository.getReferenceById(principal.getUserId());

        BigDecimal discountAmount = request.discountAmount() != null ? request.discountAmount() : BigDecimal.ZERO;
        BigDecimal taxAmount = request.taxAmount() != null ? request.taxAmount() : BigDecimal.ZERO;

        List<SalesTransactionItem> items = new ArrayList<>();
        List<StockMovement> movements = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        // SalesTransaction row must exist before items/movements can reference
        // it, but we need the computed totals first — build it once totals
        // are known, right before saving everything together at the end.
        for (CreateOrderItemRequest itemRequest : request.items()) {
            LineResult line = priceAndReserveLine(storeId, itemRequest);
            subtotal = subtotal.add(line.subtotal);
            items.add(line.item);
            movements.add(line.movement);
        }

        BigDecimal grandTotal = subtotal.subtract(discountAmount).add(taxAmount);
        if (grandTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Diskon melebihi total belanja");
        }
        if (request.paymentAmount().compareTo(grandTotal) < 0) {
            throw new BadRequestException("Jumlah pembayaran kurang dari total belanja");
        }
        BigDecimal changeAmount = request.paymentAmount().subtract(grandTotal);

        String transactionNumber = nextTransactionNumber(store);
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

        paymentRepository.save(Payment.builder()
                .transaction(transaction)
                .paymentMethod(request.paymentMethod())
                .amount(request.paymentAmount())
                .paymentStatus("SUCCESS")
                .paidAt(now)
                .createdBy(cashier)
                .createdAt(now)
                .build());

        return toResponse(transaction, items);
    }

    @Transactional
    public OrderResponse voidOrder(UUID id, AppUserPrincipal principal, String reason) {
        UUID storeId = requireStore(principal);
        SalesTransaction transaction = transactionRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaksi tidak ditemukan"));

        if (!"PAID".equals(transaction.getTransactionStatus())) {
            throw new BadRequestException("Hanya transaksi berstatus PAID yang bisa dibatalkan");
        }

        User voider = userRepository.getReferenceById(principal.getUserId());
        Instant now = Instant.now();
        List<SalesTransactionItem> items = itemRepository.findByTransactionId(id);

        for (SalesTransactionItem item : items) {
            Inventory inventory = inventoryRepository.findForUpdate(storeId, item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Stok produk tidak ditemukan"));
            BigDecimal before = inventory.getQuantityBaseUnit();
            BigDecimal after = before.add(item.getQuantityBaseUnit());
            inventory.setQuantityBaseUnit(after);
            inventory.setUpdatedAt(now);
            inventoryRepository.save(inventory);

            stockMovementRepository.save(StockMovement.builder()
                    .store(transaction.getStore())
                    .product(item.getProduct())
                    .movementType("VOID_TRANSACTION")
                    .quantity(item.getQuantity())
                    .unit(item.getUnit())
                    .quantityBaseUnit(item.getQuantityBaseUnit())
                    .stockBefore(before)
                    .stockAfter(after)
                    .referenceType("SALES_TRANSACTION")
                    .referenceId(transaction.getId())
                    .createdBy(voider)
                    .createdAt(now)
                    .build());
        }

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
        return PageResponse.of(page.map(t -> toResponse(t, itemRepository.findByTransactionId(t.getId()))));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);
        SalesTransaction transaction = transactionRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaksi tidak ditemukan"));
        return toResponse(transaction, itemRepository.findByTransactionId(id));
    }

    private LineResult priceAndReserveLine(UUID storeId, CreateOrderItemRequest itemRequest) {
        Product product = productRepository.findById(itemRequest.productId())
                .filter(p -> p.getDeletedAt() == null && "ACTIVE".equals(p.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Produk tidak ditemukan atau tidak aktif"));
        Unit unit = unitRepository.findById(itemRequest.unitId())
                .orElseThrow(() -> new ResourceNotFoundException("Satuan tidak ditemukan"));
        ProductUnitConversion conversion = conversionRepository.findByProductIdAndUnitId(product.getId(), unit.getId())
                .orElseThrow(() -> new BadRequestException("Satuan tersebut tidak berlaku untuk produk " + product.getProductName()));
        var price = priceRepository.findByStoreIdAndProductIdAndEffectiveUntilIsNull(storeId, product.getId())
                .orElseThrow(() -> new BadRequestException("Produk " + product.getProductName() + " belum punya harga di toko ini"));

        BigDecimal quantityBaseUnit = itemRequest.quantity().multiply(conversion.getConversionToBase());

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

        BigDecimal lineSubtotal = price.getSellingPrice().multiply(itemRequest.quantity());

        SalesTransactionItem item = SalesTransactionItem.builder()
                .product(product)
                .quantity(itemRequest.quantity())
                .unit(unit)
                .quantityBaseUnit(quantityBaseUnit)
                .unitPrice(price.getSellingPrice())
                .discount(BigDecimal.ZERO)
                .subtotal(lineSubtotal)
                .build();

        StockMovement movement = StockMovement.builder()
                .store(inventory.getStore())
                .product(product)
                .movementType("SALE")
                .quantity(itemRequest.quantity())
                .unit(unit)
                .quantityBaseUnit(quantityBaseUnit)
                .stockBefore(before)
                .stockAfter(after)
                .build();

        return new LineResult(item, movement, lineSubtotal);
    }

    private String nextTransactionNumber(Store store) {
        NumberSequence sequence = numberSequenceRepository.findForUpdate(store.getId(), "SALES_TRANSACTION")
                .orElseThrow(() -> new IllegalStateException(
                        "number_sequences belum di-seed untuk store " + store.getId() + " / SALES_TRANSACTION"));
        long next = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(next);
        sequence.setUpdatedAt(Instant.now());
        numberSequenceRepository.save(sequence);

        String datePart = SEQUENCE_DATE_FORMAT.format(Instant.now());
        return "%s-%s-%s-%06d".formatted(sequence.getPrefix(), store.getStoreCode(), datePart, next);
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

    private record LineResult(SalesTransactionItem item, StockMovement movement, BigDecimal subtotal) {
    }
}
