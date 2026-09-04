package com.company.prototype.api.storage;

import com.company.prototype.common.storage.ObjectStorage;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

@Service
public class MinioObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorage.class);

    private final String endpoint;
    private final String bucket;
    private final String rootUser;
    private final String rootPassword;
    private final String uploadPublicBaseUrl;

    private MinioClient client;
    private MinioClient presignClient;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.company.prototype.api.monitoring.PlatformMetrics platformMetrics;

    private void countMinioError() {
        if (platformMetrics != null) {
            try {
                platformMetrics.minioError();
            } catch (Exception ignored) {
                // 指标不可用不影响存储操作
            }
        }
    }

    public MinioObjectStorage(
        @Value("${minio.endpoint:http://localhost:9000}") String endpoint,
        @Value("${minio.bucket:prototype-objects}") String bucket,
        @Value("${minio.root-user:minio_admin}") String rootUser,
        @Value("${minio.root-password:minio_secret}") String rootPassword,
        @Value("${minio.upload-public-base-url:http://prototype.corp.test/upload-objects}") String uploadPublicBaseUrl
    ) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.rootUser = rootUser;
        this.rootPassword = rootPassword;
        this.uploadPublicBaseUrl = uploadPublicBaseUrl;
    }

    @PostConstruct
    public void init() {
        try {
            this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(rootUser, rootPassword)
                .region("us-east-1")
                .build();

            // MinIO SDK 的 endpoint 不允许带路径：用 UPLOAD_PUBLIC_BASE_URL 的
            // scheme+host 作为预签名 endpoint，生成的 URL 路径再拼回 Nginx 反代前缀。
            java.net.URI publicUri = java.net.URI.create(uploadPublicBaseUrl);
            String origin = publicUri.getScheme() + "://" + publicUri.getHost()
                + (publicUri.getPort() > 0 ? ":" + publicUri.getPort() : "");
            this.presignClient = MinioClient.builder()
                .endpoint(origin)
                .credentials(rootUser, rootPassword)
                .region("us-east-1")
                .build();

            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("Failed to initialize MinIO bucket on startup (will retry on usage): {}", e.getMessage());
        }
    }

    @Override
    public PresignedUpload createUpload(String objectKey, long maxBytes, Duration ttl) {
        try {
            // 手动 SigV4 预签名：payload hash 固定 UNSIGNED-PAYLOAD 放入 query，
            // URL 直接携带 Nginx 反代前缀，客户端无需额外签名头即可直传。
            String routedUrl = PresignedUrlSigner.signPutUrl(
                uploadPublicBaseUrl, bucket, objectKey, rootUser, rootPassword, ttl);
            return new PresignedUpload(routedUrl, objectKey, Instant.now().plus(ttl));
        } catch (Exception e) {
            countMinioError();
            throw new RuntimeException("Failed to generate presigned upload URL: " + e.getMessage(), e);
        }
    }

    @Override
    public ObjectMetadata stat(String objectKey) {
        try {
            StatObjectResponse stat = client.statObject(
                StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build()
            );
            String checksum = stat.etag() != null ? stat.etag().replace("\"", "").toLowerCase() : null;
            return new ObjectMetadata(objectKey, stat.size(), checksum, stat.contentType());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equalsIgnoreCase(e.errorResponse().code()) || "ResourceNotFound".equalsIgnoreCase(e.errorResponse().code())) {
                return null;
            }
            countMinioError();
            throw new RuntimeException("Error checking object in MinIO: " + e.getMessage(), e);
        } catch (Exception e) {
            countMinioError();
            throw new RuntimeException("Failed to stat object: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            return client.getObject(
                GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build()
            );
        } catch (Exception e) {
            countMinioError();
            throw new RuntimeException("Failed to open object stream: " + e.getMessage(), e);
        }
    }

    @Override
    public void put(String objectKey, InputStream input, long size, String contentType) {
        try {
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(input, size, -1L)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build()
            );
        } catch (Exception e) {
            countMinioError();
            throw new RuntimeException("Failed to put object: " + e.getMessage(), e);
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        try {
            Iterable<Result<Item>> results = client.listObjects(
                ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(true)
                    .build()
            );
            for (Result<Item> itemResult : results) {
                Item item = itemResult.get();
                client.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(item.objectName())
                        .build()
                );
            }
        } catch (Exception e) {
            countMinioError();
            throw new RuntimeException("Failed to delete objects with prefix " + prefix + ": " + e.getMessage(), e);
        }
    }
}
