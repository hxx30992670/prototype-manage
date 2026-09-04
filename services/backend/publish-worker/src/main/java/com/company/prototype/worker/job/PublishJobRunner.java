package com.company.prototype.worker.job;

import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.config.SystemConfigRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.publish.PublishJobEntity;
import com.company.prototype.persistence.publish.PublishJobRepository;
import com.company.prototype.persistence.publish.PublishJobStatus;
import com.company.prototype.persistence.publish.PublishStage;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import com.company.prototype.persistence.version.VersionStatus;
import com.company.prototype.worker.packagecheck.PackageLimits;
import com.company.prototype.worker.packagecheck.ZipPackageValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

@Service
public class PublishJobRunner {

    private static final Logger log = LoggerFactory.getLogger(PublishJobRunner.class);

    private final PublishJobRepository publishJobRepository;
    private final PrototypeVersionRepository versionRepository;
    private final PrototypeRepository prototypeRepository;
    private final ObjectStorage objectStorage;
    private final ZipPackageValidator zipPackageValidator;
    private final TransactionTemplate transactionTemplate;
    private final SystemConfigRepository configRepository;
    /** 测试注入的自定义限制；为 null 时每次任务从 system_config 读取。 */
    private volatile PackageLimits limits = null;

    public PublishJobRunner(
        PublishJobRepository publishJobRepository,
        PrototypeVersionRepository versionRepository,
        PrototypeRepository prototypeRepository,
        ObjectStorage objectStorage,
        ZipPackageValidator zipPackageValidator,
        PlatformTransactionManager transactionManager,
        @org.springframework.beans.factory.annotation.Autowired(required = false) SystemConfigRepository configRepository
    ) {
        this.publishJobRepository = publishJobRepository;
        this.versionRepository = versionRepository;
        this.prototypeRepository = prototypeRepository;
        this.objectStorage = objectStorage;
        this.zipPackageValidator = zipPackageValidator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.configRepository = configRepository;
    }

    public void setLimits(PackageLimits limits) {
        this.limits = limits;
    }

    /** 任务级限制解析：测试注入优先，否则从 system_config 读取，未配置项用默认值。 */
    public PackageLimits resolveLimits() {
        if (limits != null) {
            return limits;
        }
        if (configRepository == null) {
            return PackageLimits.defaultLimits();
        }
        try {
            long entryMb = configLong("upload.singleEntry.maxMb", 100);
            long totalMb = configLong("upload.expanded.maxMb", 500);
            long fileCount = configLong("upload.fileCount.max", 10000);
            long depth = configLong("upload.directoryDepth.max", 20);
            long ratio = configLong("upload.compressionRatio.max", 100);
            long timeout = configLong("upload.extractTimeout.seconds", 60);
            return new PackageLimits(
                entryMb * 1024 * 1024L,
                totalMb * 1024 * 1024L,
                (int) Math.min(fileCount, Integer.MAX_VALUE),
                (int) Math.min(depth, Integer.MAX_VALUE),
                ratio,
                timeout
            );
        } catch (Exception e) {
            log.warn("Failed to load package limits from system_config, using defaults: {}", e.getMessage());
            return PackageLimits.defaultLimits();
        }
    }

    private long configLong(String key, long defaultValue) {
        return configRepository.findByConfigKey(key)
            .map(entry -> {
                try {
                    return Long.parseLong(entry.getConfigValue());
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            })
            .orElse(defaultValue);
    }

    public void run(Long jobId) {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(Duration.ofMinutes(10));
        int claimed = publishJobRepository.tryClaim(jobId, now, staleBefore);
        if (claimed == 0) {
            log.info("Publish job {} was not claimed (already taken or finished)", jobId);
            return;
        }

        PublishJobEntity job = publishJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Job {} not found", jobId);
            return;
        }

        PrototypeVersionEntity version = job.getVersion();
        Path workDir = null;

        try {

            // 1. UPLOAD_CHECK
            job.setStage(PublishStage.UPLOAD_CHECK);
            job.setProgress(10);
            publishJobRepository.saveAndFlush(job);

            ObjectStorage.ObjectMetadata meta = objectStorage.stat(version.getSourceObjectKey());
            if (meta == null) {
                throw new RuntimeException("源对象不存在: " + version.getSourceObjectKey());
            }

            // 2. PACKAGE_CHECK & EXTRACT
            job.setStage(PublishStage.PACKAGE_CHECK);
            job.setProgress(30);
            publishJobRepository.saveAndFlush(job);

            workDir = Files.createTempDirectory("publish-worker-job-" + jobId);
            Path sourceFile = workDir.resolve("source.pkg");
            try (InputStream in = objectStorage.open(version.getSourceObjectKey());
                 OutputStream out = Files.newOutputStream(sourceFile)) {
                in.transferTo(out);
            }

            job.setStage(PublishStage.EXTRACT);
            job.setProgress(50);
            publishJobRepository.saveAndFlush(job);

            Path extractDir = workDir.resolve("extracted");
            Files.createDirectories(extractDir);

            String entryPath;
            int fileCount;
            long expandedSize;

            if ("ZIP".equalsIgnoreCase(version.getSourceType())) {
                ZipPackageValidator.ExtractionResult result = zipPackageValidator.validateAndExtract(sourceFile, extractDir, resolveLimits());
                entryPath = result.entryPath();
                fileCount = result.fileCount();
                expandedSize = result.totalExpandedBytes();
            } else {
                // HTML single file
                Path destHtml = extractDir.resolve("index.html");
                Files.copy(sourceFile, destHtml);
                entryPath = "index.html";
                fileCount = 1;
                expandedSize = Files.size(sourceFile);
            }

            // 3. STORE
            job.setStage(PublishStage.STORE);
            job.setProgress(70);
            publishJobRepository.saveAndFlush(job);

            String publishPrefix = "prototypes/" + job.getPrototype().getId() + "/versions/" + version.getId() + "/published/";
            try (Stream<Path> stream = Files.walk(extractDir)) {
                for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                    String relativePath = extractDir.relativize(file).toString().replace('\\', '/');
                    String objectKey = publishPrefix + relativePath;
                    String contentType = determineContentType(relativePath);
                    try (InputStream fis = Files.newInputStream(file)) {
                        objectStorage.put(objectKey, fis, Files.size(file), contentType);
                    }
                }
            }

            // 4. FINALIZE (Atomic commit and pointer switch with lock)
            transactionTemplate.executeWithoutResult(status -> {
                finalizePublish(job.getId(), version.getId(), job.getPrototype().getId(), publishPrefix, entryPath, fileCount, expandedSize);
            });

        } catch (Exception e) {
            log.error("Publish job {} failed: {}", jobId, e.getMessage(), e);
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    handleFailure(jobId, version.getId(), job != null ? job.getStage() : PublishStage.UPLOAD_CHECK, e.getMessage());
                });
            } catch (Exception ex) {
                log.error("Failed to record job failure: {}", ex.getMessage(), ex);
            }
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    @Transactional
    public void finalizePublish(Long jobId, Long versionId, Long prototypeId, String publishPrefix, String entryPath, int fileCount, long expandedSize) {
        PrototypeEntity prototype = prototypeRepository.findByIdForUpdate(prototypeId)
            .orElseThrow(() -> new RuntimeException("Prototype not found: " + prototypeId));

        PrototypeVersionEntity version = versionRepository.findById(versionId)
            .orElseThrow(() -> new RuntimeException("Version not found: " + versionId));

        PublishJobEntity job = publishJobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        version.setStatus(VersionStatus.PUBLISHED);
        version.setPublishPrefix(publishPrefix);
        version.setEntryPath(entryPath);
        version.setFileCount(fileCount);
        version.setExpandedSize(expandedSize);
        version.setPublishedAt(Instant.now());
        versionRepository.save(version);

        prototype.setCurrentVersionId(version.getId());
        prototypeRepository.save(prototype);

        job.setStatus(PublishJobStatus.SUCCEEDED);
        job.setStage(PublishStage.FINALIZE);
        job.setProgress(100);
        job.setFinishedAt(Instant.now());
        publishJobRepository.save(job);
    }

    @Transactional
    public void handleFailure(Long jobId, Long versionId, PublishStage failureStage, String errorMsg) {
        PublishJobEntity job = publishJobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus(PublishJobStatus.FAILED);
            job.setErrorDetail(errorMsg);
            job.setFinishedAt(Instant.now());
            publishJobRepository.save(job);
        }

        PrototypeVersionEntity version = versionRepository.findById(versionId).orElse(null);
        if (version != null) {
            version.setStatus(VersionStatus.FAILED);
            version.setFailureStage(failureStage != null ? failureStage.name() : "UNKNOWN");
            version.setFailureMessage(errorMsg != null && errorMsg.length() > 1000 ? errorMsg.substring(0, 1000) : errorMsg);
            versionRepository.save(version);
        }
    }

    private String determineContentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    private void deleteQuietly(Path path) {
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}
