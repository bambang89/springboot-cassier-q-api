package com.cassierq.api.inventory.manage;

import com.cassierq.api.access.ActionAccessService;
import com.cassierq.api.catalog.CatalogScope;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.common.storage.FileStorageService;
import com.cassierq.api.common.storage.PublicUrls;
import com.cassierq.api.domain.entity.BadStockRecord;
import com.cassierq.api.domain.entity.Inventory;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.entity.ProductPrice;
import com.cassierq.api.domain.entity.StockMovement;
import com.cassierq.api.domain.entity.StoreSetting;
import com.cassierq.api.domain.entity.Unit;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.repository.BadStockRecordRepository;
import com.cassierq.api.domain.repository.InventoryRepository;
import com.cassierq.api.domain.repository.ProductPriceRepository;
import com.cassierq.api.domain.repository.ProductUnitConversionRepository;
import com.cassierq.api.domain.repository.StockMovementRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.StoreSettingRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.BadStockItem;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.BadStockReport;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.BadStockRequest;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.LossByProduct;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.LossByReason;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.MovementItem;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.OpnameLine;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.OpnameRequest;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.OpnameResult;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.OpnameResultLine;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.StockItem;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.StockSummary;
import com.cassierq.api.security.AppUserPrincipal;
import com.cassierq.api.security.RolePolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The Inventori menu: stock list, stock movement history, stock opname and bad stock.
 * Every stock change goes through {@link #changeStock} so it is row-locked and recorded
 * in stock_movements (STOCK_OPNAME, BAD_STOCK, BAD_STOCK_CANCEL).
 */
@Service
@RequiredArgsConstructor
public class InventoryManageService {

    /** Stock at or below this counts as "low" when a product has no minimum stock set. */
    static final BigDecimal DEFAULT_LOW_STOCK = BigDecimal.valueOf(5);
    static final String KEY_REQUIRE_APPROVAL = "BAD_STOCK_REQUIRE_APPROVAL";
    static final String REF_BAD_STOCK = "BAD_STOCK";
    static final String REF_OPNAME = "STOCK_OPNAME";

    private final CatalogScope catalogScope;
    private final InventoryRepository inventoryRepository;
    private final ProductPriceRepository priceRepository;
    private final ProductUnitConversionRepository conversionRepository;
    private final StockMovementRepository movementRepository;
    private final BadStockRecordRepository badStockRepository;
    private final StoreRepository storeRepository;
    private final StoreSettingRepository storeSettingRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ActionAccessService actionAccess;

    // ── Daftar stok ──────────────────────────────────────────────────────

    /** Stock of every (non-bundle) product in the store with status and value at cost. */
    @Transactional(readOnly = true)
    public StockSummary stock(AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);
        Map<UUID, BigDecimal> costs = costPrices(storeId);
        List<StockItem> items = inventoryRepository.listForStore(storeId).stream()
                .filter(inv -> !inv.getProduct().isBundle() && "ACTIVE".equals(inv.getProduct().getStatus()))
                .map(inv -> toStockItem(inv, costs.get(inv.getProduct().getId())))
                .sorted(Comparator.comparing(StockItem::productName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        BigDecimal totalValue = items.stream()
                .map(StockItem::stockValue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StockSummary(
                items.size(),
                (int) items.stream().filter(i -> "OUT".equals(i.status())).count(),
                (int) items.stream().filter(i -> "LOW".equals(i.status())).count(),
                totalValue,
                items);
    }

    private StockItem toStockItem(Inventory inv, BigDecimal cost) {
        Product p = inv.getProduct();
        BigDecimal stock = inv.getQuantityBaseUnit();
        BigDecimal threshold = inv.getMinimumStock() != null && inv.getMinimumStock().signum() > 0
                ? inv.getMinimumStock()
                : DEFAULT_LOW_STOCK;
        String status = stock.signum() <= 0 ? "OUT" : stock.compareTo(threshold) <= 0 ? "LOW" : "OK";
        return new StockItem(p.getId(), p.getSku(), p.getProductName(), p.getCategory().getCategoryName(),
                p.getBaseUnit().getUnitName(), PublicUrls.resolve(p.getImageUrl()), stock, inv.getMinimumStock(),
                cost, cost != null ? cost.multiply(stock.max(BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP) : null,
                status);
    }

    // ── Riwayat mutasi ───────────────────────────────────────────────────

    /** Stock movements of the store, newest first, filtered by product, type and period. */
    @Transactional(readOnly = true)
    public Page<MovementItem> movements(AppUserPrincipal principal, UUID productId, String type,
            Instant from, Instant to, Pageable pageable) {
        UUID storeId = requireStore(principal);
        return movementRepository.search(storeId, productId, blankToNull(type), from, to, pageable)
                .map(m -> new MovementItem(m.getId(), m.getCreatedAt(), m.getProduct().getId(),
                        m.getProduct().getProductName(), m.getMovementType(), m.getQuantity(), m.getUnit().getUnitName(),
                        m.getQuantityBaseUnit(), m.getStockBefore(), m.getStockAfter(), m.getReferenceType(),
                        m.getReferenceId(), m.getNotes(), nameOf(m.getCreatedBy())));
    }

    // ── Stok opname ──────────────────────────────────────────────────────

    /** Sets each product's stock to the physically counted quantity and records the difference. */
    @Transactional
    public OpnameResult opname(AppUserPrincipal principal, OpnameRequest request) {
        UUID storeId = requireStore(principal);
        UUID owner = catalogScope.ownerOf(principal);
        User user = userRepository.getReferenceById(principal.getUserId());
        Map<UUID, BigDecimal> costs = costPrices(storeId);
        UUID opnameId = UUID.randomUUID();
        String note = "Stok opname" + (request.notes() != null && !request.notes().isBlank() ? ": " + request.notes().trim() : "");

        List<OpnameResultLine> lines = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        for (OpnameLine line : request.items().stream()
                .sorted(Comparator.comparing(l -> l.productId().toString())).toList()) {
            Product product = catalogScope.product(line.productId(), owner);
            if (product.isBundle()) {
                throw new BadRequestException("Paket tidak punya stok sendiri — opname produk isinya");
            }
            Inventory inventory = inventoryRepository.findForUpdate(storeId, product.getId()).orElse(null);
            BigDecimal system = inventory != null ? inventory.getQuantityBaseUnit() : BigDecimal.ZERO;
            BigDecimal difference = line.countedQuantity().subtract(system);
            if (difference.signum() != 0) {
                changeStock(storeId, product, difference, product.getBaseUnit(), difference.abs(),
                        "STOCK_OPNAME", REF_OPNAME, opnameId, note, user);
            }
            BigDecimal cost = costs.get(product.getId());
            BigDecimal value = cost != null ? cost.multiply(difference).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            totalValue = totalValue.add(value);
            lines.add(new OpnameResultLine(product.getId(), product.getProductName(), system,
                    line.countedQuantity(), difference, value));
        }
        return new OpnameResult(lines, totalValue);
    }

    // ── Bad stock ────────────────────────────────────────────────────────

    /** Bad-stock reports of the store, newest first (optionally one status). */
    @Transactional(readOnly = true)
    public List<BadStockItem> badStock(AppUserPrincipal principal, String status) {
        return badStockRepository.list(requireStore(principal), blankToNull(status)).stream().map(this::toItem).toList();
    }

    /**
     * Reports bad stock. Stock is taken out right away (status QUARANTINED) when approval is
     * not required or the reporter is the store head; otherwise it waits as PENDING.
     */
    @Transactional
    public BadStockItem reportBadStock(AppUserPrincipal principal, BadStockRequest request) {
        UUID storeId = requireStore(principal);
        UUID owner = catalogScope.ownerOf(principal);
        Product product = catalogScope.product(request.productId(), owner);
        if (product.isBundle()) {
            throw new BadRequestException("Catat bad stock untuk produk isi paketnya, bukan paketnya");
        }
        Unit unit = catalogScope.unit(request.unitId(), owner);
        BigDecimal conversion = conversionRepository.findByProductIdAndUnitId(product.getId(), unit.getId())
                .orElseThrow(() -> new BadRequestException("Satuan tersebut tidak berlaku untuk " + product.getProductName()))
                .getConversionToBase();
        BigDecimal quantityBase = request.quantity().multiply(conversion);
        BigDecimal cost = costPrices(storeId).get(product.getId());
        User reporter = userRepository.getReferenceById(principal.getUserId());

        BadStockRecord record = badStockRepository.save(BadStockRecord.builder()
                .store(storeRepository.getReferenceById(storeId))
                .product(product)
                .unit(unit)
                .quantity(request.quantity())
                .quantityBase(quantityBase)
                .reason(request.reason())
                .notes(blankToNull(request.notes()))
                .status("PENDING")
                .costValue(cost != null ? cost.multiply(quantityBase).setScale(2, RoundingMode.HALF_UP) : null)
                .reportedBy(reporter)
                .build());

        if (!requireApproval(storeId) || RolePolicy.isManager(principal)) {
            approve(record, reporter);
        }
        return toItem(record);
    }

    /** Store head approves a pending report: stock is taken out and the item is set aside. */
    @Transactional
    public BadStockItem approveBadStock(AppUserPrincipal principal, UUID id) {
        BadStockRecord record = requireRecord(principal, id);
        if (!"PENDING".equals(record.getStatus())) {
            throw new BadRequestException("Hanya laporan yang menunggu persetujuan yang bisa disetujui");
        }
        approve(record, userRepository.getReferenceById(principal.getUserId()));
        return toItem(record);
    }

    /** Marks quarantined goods as returned to a supplier (optional) or destroyed. */
    @Transactional
    public BadStockItem resolveBadStock(AppUserPrincipal principal, UUID id, String outcome, UUID supplierId, String note) {
        BadStockRecord record = requireRecord(principal, id);
        if (!"QUARANTINED".equals(record.getStatus())) {
            throw new BadRequestException("Hanya barang yang sudah dipisahkan yang bisa diretur atau dimusnahkan");
        }
        if (supplierId != null) {
            record.setSupplier(catalogScope.supplier(supplierId, catalogScope.ownerOf(principal)));
        }
        record.setStatus(outcome);
        record.setResolvedBy(userRepository.getReferenceById(principal.getUserId()));
        record.setResolvedAt(Instant.now());
        record.setResolutionNote(blankToNull(note));
        badStockRepository.save(record);
        return toItem(record);
    }

    /** Cancels a report (wrong entry or rejected); stock already taken out is put back. */
    @Transactional
    public BadStockItem cancelBadStock(AppUserPrincipal principal, UUID id, String note) {
        BadStockRecord record = requireRecord(principal, id);
        if (!"PENDING".equals(record.getStatus()) && !"QUARANTINED".equals(record.getStatus())) {
            throw new BadRequestException("Laporan yang sudah diretur/dimusnahkan tidak bisa dibatalkan");
        }
        User user = userRepository.getReferenceById(principal.getUserId());
        if (record.isStockDeducted()) {
            changeStock(record.getStore().getId(), record.getProduct(), record.getQuantityBase(), record.getUnit(),
                    record.getQuantity(), "BAD_STOCK_CANCEL", REF_BAD_STOCK, record.getId(),
                    "Batal bad stock" + (note != null && !note.isBlank() ? ": " + note.trim() : ""), user);
            record.setStockDeducted(false);
        }
        record.setStatus("CANCELLED");
        record.setResolvedBy(user);
        record.setResolvedAt(Instant.now());
        record.setResolutionNote(blankToNull(note));
        badStockRepository.save(record);
        return toItem(record);
    }

    /** Uploads or replaces the evidence photo of a report. */
    @Transactional
    public BadStockItem uploadBadStockPhoto(AppUserPrincipal principal, UUID id, MultipartFile file) {
        BadStockRecord record = requireRecord(principal, id);
        String previous = record.getPhotoUrl();
        record.setPhotoUrl(fileStorageService.storeImage(
                "stores/" + FileStorageService.slug(record.getStore().getStoreCode()) + "/bad-stock/" + id,
                record.getProduct().getSku(), file));
        badStockRepository.save(record);
        fileStorageService.deleteAfterCommit(previous);
        return toItem(record);
    }

    /** Loss at cost in a period: total, by reason, top products, and how many wait for approval. */
    @Transactional(readOnly = true)
    public BadStockReport report(AppUserPrincipal principal, Instant from, Instant to) {
        UUID storeId = requireStore(principal);
        List<BadStockRecord> records = badStockRepository.deductedBetween(storeId, from, to);
        Function<BadStockRecord, BigDecimal> value = r -> r.getCostValue() != null ? r.getCostValue() : BigDecimal.ZERO;

        Map<String, List<BadStockRecord>> byReason = records.stream()
                .collect(Collectors.groupingBy(BadStockRecord::getReason, LinkedHashMap::new, Collectors.toList()));
        List<LossByReason> reasons = byReason.entrySet().stream()
                .map(e -> new LossByReason(e.getKey(), e.getValue().size(),
                        e.getValue().stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(LossByReason::value).reversed())
                .toList();

        Map<UUID, List<BadStockRecord>> byProduct = records.stream()
                .collect(Collectors.groupingBy(r -> r.getProduct().getId()));
        List<LossByProduct> products = byProduct.values().stream()
                .map(list -> new LossByProduct(list.get(0).getProduct().getId(), list.get(0).getProduct().getProductName(),
                        list.stream().map(BadStockRecord::getQuantityBase).reduce(BigDecimal.ZERO, BigDecimal::add),
                        list.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(LossByProduct::value).reversed())
                .limit(5)
                .toList();

        int pending = badStockRepository.list(storeId, "PENDING").size();
        return new BadStockReport(records.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add),
                records.size(), reasons, products, pending);
    }

    /** Whether bad-stock reports by staff need the store head's approval (default: yes). */
    @Transactional(readOnly = true)
    public boolean requireApproval(UUID storeId) {
        return storeSettingRepository.findByStoreIdAndSettingKey(storeId, KEY_REQUIRE_APPROVAL)
                .map(StoreSetting::getSettingValue)
                .map(Boolean::parseBoolean)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean requireApproval(AppUserPrincipal principal) {
        return requireApproval(requireStore(principal));
    }

    /** Turns the approval step on or off (store head). */
    @Transactional
    public boolean setRequireApproval(AppUserPrincipal principal, boolean value) {
        UUID storeId = requireStore(principal);
        StoreSetting setting = storeSettingRepository.findByStoreIdAndSettingKey(storeId, KEY_REQUIRE_APPROVAL)
                .orElseGet(() -> StoreSetting.builder()
                        .store(storeRepository.getReferenceById(storeId))
                        .settingKey(KEY_REQUIRE_APPROVAL)
                        .build());
        setting.setSettingValue(String.valueOf(value));
        storeSettingRepository.save(setting);
        return value;
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void approve(BadStockRecord record, User approver) {
        changeStock(record.getStore().getId(), record.getProduct(), record.getQuantityBase().negate(), record.getUnit(),
                record.getQuantity(), "BAD_STOCK", REF_BAD_STOCK, record.getId(),
                "Bad stock: " + reasonLabel(record.getReason())
                        + (record.getNotes() != null ? " — " + record.getNotes() : ""),
                approver);
        record.setStockDeducted(true);
        record.setStatus("QUARANTINED");
        record.setApprovedBy(approver);
        record.setApprovedAt(Instant.now());
        badStockRepository.save(record);
    }

    /**
     * Adds {@code deltaBase} (negative = takes out) to a product's stock under a row lock
     * and records the movement. Taking out more than the stock is refused.
     */
    private void changeStock(UUID storeId, Product product, BigDecimal deltaBase, Unit unit, BigDecimal quantity,
            String movementType, String referenceType, UUID referenceId, String notes, User user) {
        Inventory inventory = inventoryRepository.findForUpdate(storeId, product.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .store(storeRepository.getReferenceById(storeId))
                        .product(product)
                        .quantityBaseUnit(BigDecimal.ZERO)
                        .minimumStock(BigDecimal.ZERO)
                        .updatedAt(Instant.now())
                        .build()));
        BigDecimal before = inventory.getQuantityBaseUnit();
        BigDecimal after = before.add(deltaBase);
        if (after.signum() < 0) {
            throw new BadRequestException("Stok " + product.getProductName() + " tidak cukup (tersisa "
                    + before.stripTrailingZeros().toPlainString() + ")");
        }
        inventory.setQuantityBaseUnit(after);
        inventory.setUpdatedAt(Instant.now());
        inventoryRepository.save(inventory);

        movementRepository.save(StockMovement.builder()
                .store(inventory.getStore())
                .product(product)
                .movementType(movementType)
                .quantity(quantity)
                .unit(unit)
                .quantityBaseUnit(deltaBase.abs())
                .stockBefore(before)
                .stockAfter(after)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .notes(notes != null && notes.length() > 1000 ? notes.substring(0, 1000) : notes)
                .createdBy(user)
                .createdAt(Instant.now())
                .build());
    }

    private Map<UUID, BigDecimal> costPrices(UUID storeId) {
        Map<UUID, BigDecimal> map = new java.util.HashMap<>();
        for (ProductPrice price : priceRepository.findByStoreIdAndEffectiveUntilIsNull(storeId)) {
            if (price.getCostPrice() != null) map.put(price.getProduct().getId(), price.getCostPrice());
        }
        return map;
    }

    private BadStockItem toItem(BadStockRecord r) {
        return new BadStockItem(r.getId(), r.getCreatedAt(), r.getProduct().getId(), r.getProduct().getProductName(),
                r.getQuantity(), r.getUnit().getUnitName(), r.getQuantityBase(), r.getReason(), r.getNotes(),
                PublicUrls.resolve(r.getPhotoUrl()), r.getStatus(), r.getCostValue(), r.isStockDeducted(),
                nameOf(r.getReportedBy()), nameOf(r.getApprovedBy()), r.getApprovedAt(),
                r.getSupplier() != null ? r.getSupplier().getSupplierName() : null, r.getResolutionNote());
    }

    private BadStockRecord requireRecord(AppUserPrincipal principal, UUID id) {
        return badStockRepository.findByIdAndStoreId(id, requireStore(principal))
                .orElseThrow(() -> new ResourceNotFoundException("Laporan bad stock tidak ditemukan"));
    }

    static String reasonLabel(String reason) {
        return switch (reason) {
            case "DAMAGED" -> "Rusak / pecah";
            case "EXPIRED" -> "Kedaluwarsa";
            case "LOST" -> "Hilang";
            case "DEFECT" -> "Cacat produksi";
            case "CUSTOMER_RETURN" -> "Retur pembeli";
            default -> reason;
        };
    }

    private static String nameOf(User user) {
        if (user == null) return null;
        return user.getEmployee() != null ? user.getEmployee().getFullName() : user.getUsername();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UUID requireStore(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Pilih toko terlebih dahulu");
        }
        return storeId;
    }
}
