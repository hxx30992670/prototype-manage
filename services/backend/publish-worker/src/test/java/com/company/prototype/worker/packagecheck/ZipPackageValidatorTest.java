package com.company.prototype.worker.packagecheck;

import com.company.prototype.common.error.ApiErrorCode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipPackageValidatorTest {

    private final ZipPackageValidator validator = new ZipPackageValidator();
    private static Path staticTempDir;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        staticTempDir = tempDir;
    }

    @Test
    void validatesAndExtractsValidPackage() throws IOException {
        Path validZip = tempDir.resolve("valid.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(validZip))) {
            addEntry(zos, "index.html", "<html><body>Hello</body></html>".getBytes(StandardCharsets.UTF_8));
            addEntry(zos, "css/style.css", "body { color: red; }".getBytes(StandardCharsets.UTF_8));
        }

        Path target = tempDir.resolve("extracted");
        var result = validator.validateAndExtract(validZip, target, PackageLimits.defaultLimits());

        assertThat(result.entryPath()).isEqualTo("index.html");
        assertThat(result.fileCount()).isEqualTo(2);
        assertThat(Files.exists(target.resolve("index.html"))).isTrue();
        assertThat(Files.exists(target.resolve("css/style.css"))).isTrue();
    }

    @ParameterizedTest
    @MethodSource("maliciousPackages")
    void rejectsUnsafeArchive(Path zip, PackageLimits limits, ApiErrorCode expected) {
        Path target = tempDir.resolve("extracted_" + System.nanoTime());
        assertThatThrownBy(() -> validator.validateAndExtract(zip, target, limits))
            .isInstanceOf(PackageValidationException.class)
            .extracting("code").isEqualTo(expected);
    }

    static Stream<Arguments> maliciousPackages() throws IOException {
        Path base = Files.createTempDirectory("zip-test-pkgs");

        // 1. Path traversal (Zip Slip)
        Path slipZip = base.resolve("zipslip.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(slipZip))) {
            addEntry(zos, "../evil.txt", "evil".getBytes(StandardCharsets.UTF_8));
            addEntry(zos, "index.html", "ok".getBytes(StandardCharsets.UTF_8));
        }

        // 2. Absolute path
        Path absZip = base.resolve("absolute.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(absZip))) {
            addEntry(zos, "/etc/passwd", "evil".getBytes(StandardCharsets.UTF_8));
            addEntry(zos, "index.html", "ok".getBytes(StandardCharsets.UTF_8));
        }

        // 3. Single entry size limit
        Path largeEntryZip = base.resolve("large_entry.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(largeEntryZip))) {
            addEntry(zos, "huge.txt", new byte[2000]);
            addEntry(zos, "index.html", "ok".getBytes(StandardCharsets.UTF_8));
        }
        PackageLimits entryLimits = new PackageLimits(1000L, 50000L, 100, 10, 100.0, 60);

        // 4. Total expanded size limit
        Path totalSizeZip = base.resolve("total_size.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(totalSizeZip))) {
            addEntry(zos, "f1.txt", new byte[1500]);
            addEntry(zos, "f2.txt", new byte[1500]);
            addEntry(zos, "index.html", "ok".getBytes(StandardCharsets.UTF_8));
        }
        PackageLimits totalLimits = new PackageLimits(2000L, 2500L, 100, 10, 100.0, 60);

        // 5. File count limit
        Path fileCountZip = base.resolve("file_count.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(fileCountZip))) {
            addEntry(zos, "f1.txt", "a".getBytes(StandardCharsets.UTF_8));
            addEntry(zos, "f2.txt", "b".getBytes(StandardCharsets.UTF_8));
            addEntry(zos, "f3.txt", "c".getBytes(StandardCharsets.UTF_8));
            addEntry(zos, "index.html", "ok".getBytes(StandardCharsets.UTF_8));
        }
        PackageLimits countLimits = new PackageLimits(50000L, 50000L, 2, 10, 100.0, 60);

        // 6. Directory depth limit
        Path depthZip = base.resolve("depth.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(depthZip))) {
            addEntry(zos, "a/b/c/d/e/f/index.html", "ok".getBytes(StandardCharsets.UTF_8));
        }
        PackageLimits depthLimits = new PackageLimits(50000L, 50000L, 100, 3, 100.0, 60);

        // 7. Missing index.html
        Path noIndexZip = base.resolve("no_index.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(noIndexZip))) {
            addEntry(zos, "hello.txt", "world".getBytes(StandardCharsets.UTF_8));
        }

        // 8. Timeout limit
        Path timeoutZip = base.resolve("timeout.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(timeoutZip))) {
            addEntry(zos, "index.html", "ok".getBytes(StandardCharsets.UTF_8));
        }
        PackageLimits timeoutLimits = new PackageLimits(50000L, 50000L, 100, 10, 100.0, 0);

        return Stream.of(
            Arguments.of(slipZip, PackageLimits.defaultLimits(), ApiErrorCode.ZIP_SLIP_ATTEMPT),
            Arguments.of(absZip, PackageLimits.defaultLimits(), ApiErrorCode.ZIP_SLIP_ATTEMPT),
            Arguments.of(largeEntryZip, entryLimits, ApiErrorCode.ZIP_ENTRY_SIZE_LIMIT),
            Arguments.of(totalSizeZip, totalLimits, ApiErrorCode.ZIP_EXPANDED_SIZE_LIMIT),
            Arguments.of(fileCountZip, countLimits, ApiErrorCode.ZIP_FILE_COUNT_LIMIT),
            Arguments.of(depthZip, depthLimits, ApiErrorCode.ZIP_DIRECTORY_DEPTH_LIMIT),
            Arguments.of(noIndexZip, PackageLimits.defaultLimits(), ApiErrorCode.ZIP_ENTRY_INVALID),
            Arguments.of(timeoutZip, timeoutLimits, ApiErrorCode.ZIP_EXTRACT_TIMEOUT)
        );
    }

    private static void addEntry(ZipArchiveOutputStream zos, String name, byte[] content) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setSize(content.length);
        zos.putArchiveEntry(entry);
        zos.write(content);
        zos.closeArchiveEntry();
    }
}
