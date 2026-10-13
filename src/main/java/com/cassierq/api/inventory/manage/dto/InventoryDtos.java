package com.cassierq.api.inventory.manage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response shapes of the Inventori menu. */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    /** One product in the stock list. status = OUT, LOW or OK. */
    public record StockItem(
            UUID productId,
            String sku,
            String productName,
            String categoryName,
            String baseUnitName,
            String imageUrl,
            BigDecimal stock,
            BigDecimal minimumStock,
            BigDecimal costPrice,
            BigDecimal stockValue,
            String status) {
    }

    public record StockSummary(int products, int outOfStock, int lowStock, BigDecimal totalValue, List<StockItem> items) {
    }

    public record MovementItem(
            UUID id,
            Instant createdAt,
            UUID productId,
            String productName,
            String movementType,
            BigDecimal quantity,
            String unitName,
            BigDecimal quantityBase,
            BigDecimal stockBefore,
            BigDecimal stockAfter,
            String referenceType,
            UUID referenceId,
            String notes,
            String createdBy) {
    }

    public record OpnameLine(
            @NotNull(message = "Produk wajib dipilih") UUID productId,
            @NotNull(message = "Jumlah fisik wajib diisi")
            @DecimalMin(value = "0", message = "Jumlah fisik tidak boleh negatif")
            BigDecimal countedQuantity) {
    }

    public record OpnameRequest(
            @NotEmpty(message = "Pilih minimal satu produk") @Valid List<OpnameLine> items,
            @Size(max = 500) String notes) {
    }

    /** Result per product: system stock vs counted, and the difference applied. */
    public record OpnameResultLine(
            UUID productId,
            String productName,
            BigDecimal systemQuantity,
            BigDecimal countedQuantity,
            BigDecimal difference,
            BigDecimal differenceValue) {
    }

    public record OpnameResult(List<OpnameResultLine> lines, BigDecimal totalDifferenceValue) {
    }

    public record BadStockRequest(
            @NotNull(message = "Produk wajib dipilih") UUID productId,
            @NotNull(message = "Satuan wajib dipilih") UUID unitId,
            @NotNull(message = "Jumlah wajib diisi")
            @DecimalMin(value = "0", inclusive = false, message = "Jumlah harus lebih dari 0")
            BigDecimal quantity,
            @NotNull(message = "Alasan wajib dipilih")
            @Pattern(regexp = "DAMAGED|EXPIRED|LOST|DEFECT|CUSTOMER_RETURN", message = "Alasan tidak dikenal")
            String reason,
            @Size(max = 500) String notes) {
    }

    public record ResolveRequest(UUID supplierId, @Size(max = 500) String note) {
    }

    public record BadStockItem(
            UUID id,
            Instant createdAt,
            UUID productId,
            String productName,
            BigDecimal quantity,
            String unitName,
            BigDecimal quantityBase,
            String reason,
            String notes,
            String photoUrl,
            String status,
            BigDecimal costValue,
            boolean stockDeducted,
            String reportedBy,
            String approvedBy,
            Instant approvedAt,
            String supplierName,
            String resolutionNote) {
    }

    public record LossByReason(String reason, int count, BigDecimal value) {
    }

    public record LossByProduct(UUID productId, String productName, BigDecimal quantityBase, BigDecimal value) {
    }

    public record BadStockReport(
            BigDecimal totalValue,
            int records,
            List<LossByReason> byReason,
            List<LossByProduct> topProducts,
            int pending) {
    }

    public record BadStockSettings(boolean requireApproval) {
    }
}
