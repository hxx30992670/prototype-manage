package com.company.prototype.worker.packagecheck;

import com.company.prototype.common.error.ApiErrorCode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.stream.Stream;

@Component
public class ZipPackageValidator {

    public record ExtractionResult(String entryPath, int fileCount, long totalExpandedBytes) {}

    public ExtractionResult validateAndExtract(Path zipFile, Path targetDir, PackageLimits limits) {
        long startTime = System.currentTimeMillis();
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();

        try {
            Files.createDirectories(normalizedTarget);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create target extraction directory: " + e.getMessage(), e);
        }

        int fileCount = 0;
        long totalExpandedBytes = 0;

        try (ZipFile zip = ZipFile.builder().setPath(zipFile).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();

            while (entries.hasMoreElements()) {
                if (System.currentTimeMillis() - startTime >= limits.timeoutSeconds() * 1000) {
                    throw new PackageValidationException(ApiErrorCode.ZIP_EXTRACT_TIMEOUT);
                }

                ZipArchiveEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name == null || name.isBlank()) {
                    continue;
                }

                // Check directory depth
                String[] parts = name.split("[/\\\\]+");
                if (parts.length > limits.maxDirectoryDepth()) {
                    throw new PackageValidationException(ApiErrorCode.ZIP_DIRECTORY_DEPTH_LIMIT);
                }

                // Check for absolute path or path traversal
                if (name.startsWith("/") || name.startsWith("\\") || (name.length() > 2 && name.charAt(1) == ':')) {
                    throw new PackageValidationException(ApiErrorCode.ZIP_SLIP_ATTEMPT);
                }

                Path resolved = normalizedTarget.resolve(name).normalize();
                if (!resolved.startsWith(normalizedTarget)) {
                    throw new PackageValidationException(ApiErrorCode.ZIP_SLIP_ATTEMPT);
                }

                // Check Unix mode and symlinks
                if (entry.isUnixSymlink()) {
                    throw new PackageValidationException(ApiErrorCode.ZIP_SLIP_ATTEMPT);
                }
                int mode = entry.getUnixMode();
                if (mode != 0 && (mode & 0120000) == 0120000) {
                    throw new PackageValidationException(ApiErrorCode.ZIP_SLIP_ATTEMPT);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                    continue;
                }

                // It's a file entry
                fileCount++;
                if (fileCount > limits.maxFileCount()) {
                    throw new PackageValidationException(ApiErrorCode.ZIP_FILE_COUNT_LIMIT);
                }

                if (resolved.getParent() != null) {
                    Files.createDirectories(resolved.getParent());
                }

                long entryBytes = 0;
                long compressedSize = entry.getCompressedSize();

                try (InputStream is = zip.getInputStream(entry);
                     OutputStream os = Files.newOutputStream(resolved)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        if (System.currentTimeMillis() - startTime >= limits.timeoutSeconds() * 1000) {
                            throw new PackageValidationException(ApiErrorCode.ZIP_EXTRACT_TIMEOUT);
                        }

                        entryBytes += len;
                        totalExpandedBytes += len;

                        if (entryBytes > limits.maxSingleEntryBytes()) {
                            throw new PackageValidationException(ApiErrorCode.ZIP_ENTRY_SIZE_LIMIT);
                        }
                        if (totalExpandedBytes > limits.maxTotalExpandedBytes()) {
                            throw new PackageValidationException(ApiErrorCode.ZIP_EXPANDED_SIZE_LIMIT);
                        }

                        if (compressedSize > 0 && entryBytes > 1024 * 1024L) {
                            double ratio = (double) entryBytes / (double) compressedSize;
                            if (ratio > limits.maxCompressionRatio()) {
                                throw new PackageValidationException(ApiErrorCode.ZIP_COMPRESSION_RATIO_LIMIT);
                            }
                        }

                        os.write(buffer, 0, len);
                    }
                }
            }
        } catch (PackageValidationException pve) {
            throw pve;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read ZIP archive: " + e.getMessage(), e);
        }

        // Verify index.html exists
        String entryPath = findEntryHtml(normalizedTarget);
        if (entryPath == null) {
            throw new PackageValidationException(ApiErrorCode.ZIP_ENTRY_INVALID);
        }

        return new ExtractionResult(entryPath, fileCount, totalExpandedBytes);
    }

    private String findEntryHtml(Path root) {
        if (Files.exists(root.resolve("index.html"))) {
            return "index.html";
        }
        try (Stream<Path> stream = Files.walk(root, 3)) {
            return stream.filter(p -> p.getFileName().toString().equalsIgnoreCase("index.html"))
                .map(root::relativize)
                .map(p -> p.toString().replace('\\', '/'))
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
