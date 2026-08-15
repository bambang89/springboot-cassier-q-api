package com.cassierq.api.purchasing;

import com.cassierq.api.common.NumberSequenceService;
import com.cassierq.api.common.PageResponse;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.Inventory;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.entity.ProductUnitConversion;
import com.cassierq.api.domain.entity.PurchaseOrder;
import com.cassierq.api.domain.entity.PurchaseOrderItem;
import com.cassierq.api.domain.entity.StockMovement;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.Supplier;
import com.cassierq.api.domain.entity.Unit;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.repository.InventoryRepository;
import com.cassierq.api.domain.repository.ProductRepository;
import com.cassierq.api.domain.repository.ProductUnitConversionRepository;
import com.cassierq.api.domain.repository.PurchaseOrderItemRepository;
import com.cassierq.api.domain.repository.PurchaseOrderRepository;
import com.cassierq.api.domain.repository.StockMovementRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.SupplierRepository;
import com.cassierq.api.domain.repository.UnitRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.purchasing.dto.CreatePurchaseOrderItemRequest;
import com.cassierq.api.purchasing.dto.CreatePurchaseOrderRequest;
import com.cassierq.api.purchasing.dto.PurchaseOrderItemResponse;
import com.cassierq.api.purchasing.dto.PurchaseOrderResponse;
import com.cassierq.api.purchasing.dto.ReceiveItemRequest;
import com.cassierq.api.purchasing.dto.ReceivePurchaseOrderRequest;
import com.cassierq.api.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final ProductUnitConversionRepository conversionRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final NumberSequenceService numberSequenceService;

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request, AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Toko tidak ditemukan"));
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .filter(Supplier::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier tidak ditemukan atau tidak aktif"));
        User creator = userRepository.getReferenceById(principal.getUserId());

        String poNumber = numberSequenceService.next(store, "PURCHASE_ORDER");

        PurchaseOrder po = purchaseOrderRepository.save(PurchaseOrder.builder()
                .poNumber(poNumber)
                .store(store)
                .supplier(supplier)
                .orderDate(Instant.now())
                .expectedDate(request.expectedDate())
                .status("ORDERED")
                .notes(request.notes())
                .createdBy(creator)
                .build());

        List<PurchaseOrderItem> items = request.items().stream()
                .map(itemRequest -> buildItem(po, itemRequest))
                .toList();
        purchaseOrderItemRepository.saveAll(items);

        return toResponse(po, items);
    }

    @Transactional
    public PurchaseOrderResponse receive(UUID id, ReceivePurchaseOrderRequest request, AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);
        PurchaseOrder po = purchaseOrderRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order tidak ditemukan"));

        if ("RECEIVED".equals(po.getStatus()) || "CANCELLED".equals(po.getStatus())) {
            throw new BadRequestException("Purchase order berstatus " + po.getStatus() + " tidak bisa menerima barang lagi");
        }

        List<PurchaseOrderItem> allItems = purchaseOrderItemRepository.findByPurchaseOrderId(id);
        Map<UUID, PurchaseOrderItem> itemsById = allItems.stream()
                .collect(java.util.stream.Collectors.toMap(PurchaseOrderItem::getId, i -> i));

        User receiver = userRepository.getReferenceById(principal.getUserId());
        Instant now = Instant.now();

        for (ReceiveItemRequest line : request.items()) {
            PurchaseOrderItem item = itemsById.get(line.purchaseOrderItemId());
            if (item == null) {
                throw new BadRequestException("Item PO tidak ditemukan: " + line.purchaseOrderItemId());
            }

            ProductUnitConversion conversion = conversionRepository.findByProductIdAndUnitId(item.getProduct().getId(), item.getUnit().getId())
                    .orElseThrow(() -> new BadRequestException("Konversi satuan tidak ditemukan untuk " + item.getProduct().getProductName()));
            BigDecimal receivedBaseQty = line.receivedQuantity().multiply(conversion.getConversionToBase());

            BigDecimal newReceivedTotal = item.getReceivedQuantityBaseUnit().add(receivedBaseQty);
            if (newReceivedTotal.compareTo(item.getQuantityBaseUnit()) > 0) {
                throw new BadRequestException("Jumlah diterima untuk " + item.getProduct().getProductName() + " melebihi jumlah yang dipesan");
            }
            item.setReceivedQuantityBaseUnit(newReceivedTotal);
            purchaseOrderItemRepository.save(item);

            Inventory inventory = inventoryRepository.findForUpdate(storeId, item.getProduct().getId())
                    .orElseGet(() -> Inventory.builder()
                            .store(po.getStore())
                            .product(item.getProduct())
                            .quantityBaseUnit(BigDecimal.ZERO)
                            .minimumStock(BigDecimal.ZERO)
                            .build());
            BigDecimal before = inventory.getQuantityBaseUnit();
            BigDecimal after = before.add(receivedBaseQty);
            inventory.setQuantityBaseUnit(after);
            inventory.setUpdatedAt(now);
            inventoryRepository.save(inventory);

            stockMovementRepository.save(StockMovement.builder()
                    .store(po.getStore())
                    .product(item.getProduct())
                    .movementType("PURCHASE")
                    .quantity(line.receivedQuantity())
                    .unit(item.getUnit())
                    .quantityBaseUnit(receivedBaseQty)
                    .stockBefore(before)
                    .stockAfter(after)
                    .referenceType("PURCHASE_ORDER")
                    .referenceId(po.getId())
                    .createdBy(receiver)
                    .createdAt(now)
                    .build());
        }

        boolean fullyReceived = allItems.stream()
                .allMatch(i -> i.getReceivedQuantityBaseUnit().compareTo(i.getQuantityBaseUnit()) >= 0);
        boolean anyReceived = allItems.stream()
                .anyMatch(i -> i.getReceivedQuantityBaseUnit().compareTo(BigDecimal.ZERO) > 0);
        po.setStatus(fullyReceived ? "RECEIVED" : anyReceived ? "PARTIALLY_RECEIVED" : po.getStatus());
        purchaseOrderRepository.save(po);

        return toResponse(po, allItems);
    }

    @Transactional
    public PurchaseOrderResponse cancel(UUID id, AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);
        PurchaseOrder po = purchaseOrderRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order tidak ditemukan"));

        if (!("DRAFT".equals(po.getStatus()) || "ORDERED".equals(po.getStatus()))) {
            throw new BadRequestException("Purchase order berstatus " + po.getStatus() + " tidak bisa dibatalkan");
        }

        po.setStatus("CANCELLED");
        purchaseOrderRepository.save(po);
        return toResponse(po, purchaseOrderItemRepository.findByPurchaseOrderId(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(AppUserPrincipal principal, Pageable pageable) {
        UUID storeId = requireStore(principal);
        Page<PurchaseOrder> page = purchaseOrderRepository.findByStoreIdOrderByOrderDateDesc(storeId, pageable);
        return PageResponse.of(page.map(po -> toResponse(po, purchaseOrderItemRepository.findByPurchaseOrderId(po.getId()))));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(UUID id, AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);
        PurchaseOrder po = purchaseOrderRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order tidak ditemukan"));
        return toResponse(po, purchaseOrderItemRepository.findByPurchaseOrderId(id));
    }

    private PurchaseOrderItem buildItem(PurchaseOrder po, CreatePurchaseOrderItemRequest request) {
        Product product = productRepository.findById(request.productId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Produk tidak ditemukan"));
        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResourceNotFoundException("Satuan tidak ditemukan"));
        ProductUnitConversion conversion = conversionRepository.findByProductIdAndUnitId(product.getId(), unit.getId())
                .orElseThrow(() -> new BadRequestException("Satuan tersebut tidak berlaku untuk produk " + product.getProductName()));

        BigDecimal quantityBaseUnit = request.quantity().multiply(conversion.getConversionToBase());
        BigDecimal subtotal = request.quantity().multiply(request.unitCost());

        return PurchaseOrderItem.builder()
                .purchaseOrder(po)
                .product(product)
                .quantity(request.quantity())
                .unit(unit)
                .quantityBaseUnit(quantityBaseUnit)
                .unitCost(request.unitCost())
                .receivedQuantityBaseUnit(BigDecimal.ZERO)
                .subtotal(subtotal)
                .build();
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder po, List<PurchaseOrderItem> items) {
        return PurchaseOrderResponse.from(po, items.stream().map(PurchaseOrderItemResponse::from).toList());
    }

    private UUID requireStore(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Akun ini tidak terikat ke toko manapun");
        }
        return storeId;
    }
}
