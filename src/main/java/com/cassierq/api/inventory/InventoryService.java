package com.cassierq.api.inventory;

import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.Inventory;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.entity.ProductUnitConversion;
import com.cassierq.api.domain.entity.StockMovement;
import com.cassierq.api.domain.entity.Unit;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.repository.InventoryRepository;
import com.cassierq.api.domain.repository.ProductRepository;
import com.cassierq.api.domain.repository.ProductUnitConversionRepository;
import com.cassierq.api.domain.repository.StockMovementRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.UnitRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.inventory.dto.RestockRequest;
import com.cassierq.api.inventory.dto.StockResponse;
import com.cassierq.api.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final ProductUnitConversionRepository conversionRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    /**
     * Manual stock-in — the only way to add stock right now, since Purchase
     * Orders aren't implemented (see README "What's deliberately out of
     * scope"). Records a {@code STOCK_IN} movement either way, so the trail
     * looks the same whether or not a formal PO exists.
     */
    @Transactional
    public StockResponse restock(UUID productId, RestockRequest request, AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);

        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Produk tidak ditemukan"));
        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResourceNotFoundException("Satuan tidak ditemukan"));
        ProductUnitConversion conversion = conversionRepository.findByProductIdAndUnitId(productId, unit.getId())
                .orElseThrow(() -> new BadRequestException("Satuan tersebut tidak berlaku untuk produk " + product.getProductName()));

        BigDecimal quantityBaseUnit = request.quantity().multiply(conversion.getConversionToBase());

        Inventory inventory = inventoryRepository.findForUpdate(storeId, productId).orElse(null);
        BigDecimal before = inventory != null ? inventory.getQuantityBaseUnit() : BigDecimal.ZERO;
        BigDecimal after = before.add(quantityBaseUnit);
        Instant now = Instant.now();

        if (inventory == null) {
            // First time this store carries this (global) product.
            inventory = Inventory.builder()
                    .store(storeRepository.getReferenceById(storeId))
                    .product(product)
                    .quantityBaseUnit(after)
                    .minimumStock(BigDecimal.ZERO)
                    .updatedAt(now)
                    .build();
        } else {
            inventory.setQuantityBaseUnit(after);
            inventory.setUpdatedAt(now);
        }
        inventory = inventoryRepository.save(inventory);

        User actor = userRepository.getReferenceById(principal.getUserId());
        stockMovementRepository.save(StockMovement.builder()
                .store(inventory.getStore())
                .product(product)
                .movementType("STOCK_IN")
                .quantity(request.quantity())
                .unit(unit)
                .quantityBaseUnit(quantityBaseUnit)
                .stockBefore(before)
                .stockAfter(after)
                .referenceType("MANUAL")
                .notes(request.notes())
                .createdBy(actor)
                .createdAt(now)
                .build());

        return StockResponse.from(inventory);
    }

    private UUID requireStore(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Akun ini tidak terikat ke toko manapun");
        }
        return storeId;
    }
}
