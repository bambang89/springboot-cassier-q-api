package com.cassierq.api.common.storage;

import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.config.UploadProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Menyimpan gambar upload di disk, di bawah {@code app.upload.base-dir}, dan
 * mengembalikan URL relatif {@code /uploads/...} untuk disimpan di DB
 * (diserve lewat WebMvcConfig, diubah jadi URL absolut oleh {@link PublicUrls}).
 *
 * <p>Susunan folder ditentukan pemanggil lewat {@code folder}; untuk produk:
 * <pre>
 * uploads/stores/{kode-toko}/products/{id-produk}/main/     foto utama
 * uploads/stores/{kode-toko}/products/{id-produk}/gallery/  foto tambahan
 * </pre>
 * Nama file = {@code {label}-{yyyyMMdd-HHmmss}-{acak6}.{ext}}, mis.
 * {@code sku-001-20261013-121530-a1b2c3.jpg}, supaya mudah dikenali saat
 * membuka folder di server tapi tetap unik.
 */
@Slf4j
@Service
public class FileStorageService {

    public static final String URL_PREFIX = "/uploads/";

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");

    private final Path baseDir;

    public FileStorageService(UploadProperties uploadProperties) {
        this.baseDir = Path.of(uploadProperties.baseDir()).toAbsolutePath().normalize();
    }

    /** Bagian path yang aman dan mudah dibaca: huruf kecil, angka, dan tanda hubung. */
    public static String slug(String value) {
        if (value == null) {
            return "";
        }
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    /**
     * Simpan gambar di {baseDir}/{folder}/ dan kembalikan URL relatifnya
     * (mis. "/uploads/stores/hdy-01/products/{id}/main/sku-001-20261013-121530-a1b2c3.jpg").
     */
    public String storeImage(String folder, String label, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File gambar wajib diisi");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Format gambar harus JPG, PNG, atau WEBP");
        }

        Path targetDir = resolveInsideBase(folder);
        String name = slug(label);
        String filename = (name.isEmpty() ? "foto" : name)
                + "-" + ZonedDateTime.now(ZONE).format(STAMP)
                + "-" + UUID.randomUUID().toString().substring(0, 6)
                + extensionFor(contentType);

        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetDir.resolve(filename));
            return URL_PREFIX + folder + "/" + filename;
        } catch (IOException ex) {
            log.error("Gagal menyimpan file upload", ex);
            throw new BadRequestException("Gagal menyimpan file, coba lagi");
        }
    }

    /**
     * Hapus file milik URL relatif {@code /uploads/...} setelah transaksi DB
     * berhasil commit (kalau rollback, file tetap ada dan masih dirujuk DB).
     * URL lain (mis. gambar eksternal) diabaikan.
     */
    public void deleteAfterCommit(String storedUrl) {
        if (storedUrl == null || !storedUrl.startsWith(URL_PREFIX)) {
            return;
        }
        Runnable delete = () -> {
            try {
                Files.deleteIfExists(resolveInsideBase(storedUrl.substring(URL_PREFIX.length())));
            } catch (IOException | BadRequestException ex) {
                log.warn("Gagal menghapus file upload lama {}", storedUrl, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
    }

    private Path resolveInsideBase(String relative) {
        Path path = baseDir.resolve(relative).normalize();
        if (!path.startsWith(baseDir)) {
            throw new BadRequestException("Lokasi file tidak valid");
        }
        return path;
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
