package com.cassierq.api.common.storage;

import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.config.UploadProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path baseDir;

    public FileStorageService(UploadProperties uploadProperties) {
        this.baseDir = Path.of(uploadProperties.baseDir()).toAbsolutePath().normalize();
    }

    /**
     * Simpan gambar di bawah {baseDir}/{subDir}/ dengan nama acak, dan kembalikan
     * path relatif (mis. "products/{id}/xxxx.jpg") — dipetakan ke URL publik
     * lewat WebMvcConfig (/uploads/**).
     */
    public String storeImage(String subDir, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File gambar wajib diisi");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Format gambar harus JPG, PNG, atau WEBP");
        }

        Path targetDir = baseDir.resolve(subDir).normalize();
        if (!targetDir.startsWith(baseDir)) {
            throw new BadRequestException("Lokasi file tidak valid");
        }

        try {
            Files.createDirectories(targetDir);
            String filename = UUID.randomUUID() + extensionFor(contentType);
            Path target = targetDir.resolve(filename);
            file.transferTo(target);
            return subDir + "/" + filename;
        } catch (IOException ex) {
            log.error("Gagal menyimpan file upload", ex);
            throw new BadRequestException("Gagal menyimpan file, coba lagi");
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
