package com.company.prototype.gateway.storage;

import com.company.prototype.common.storage.ObjectStorage;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;

@Service
public class GatewayMinioObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(GatewayMinioObjectStorage.class);

    private final String endpoint;
    private final String bucket;
    private final String rootUser;
    private final String rootPassword;

    private MinioClient client;

    public GatewayMinioObjectStorage(
        @Value("${minio.endpoint:http://localhost:9000}") String endpoint,
        @Value("${minio.bucket:prototype-objects}") String bucket,
        @Value("${minio.root-user:minio_admin}") String rootUser,
        @Value("${minio.root-password:minio_secret}") String rootPassword
    ) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.rootUser = rootUser;
        this.rootPassword = rootPassword;
    }

    @PostConstruct
    public void init() {
        try {
            this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(rootUser, rootPassword)
                .build();
        } catch (Exception e) {
            log.warn("Failed to initialize MinIO client in preview-gateway: {}", e.getMessage());
        }
    }

    @Override
    public PresignedUpload createUpload(String objectKey, long maxBytes, Duration ttl) {
        throw new UnsupportedOperationException("Gateway does not handle uploads");
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
            throw new RuntimeException("Error checking object in MinIO: " + e.getMessage(), e);
        } catch (Exception e) {
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
            throw new RuntimeException("Failed to open object stream: " + e.getMessage(), e);
        }
    }

    @Override
    public void put(String objectKey, InputStream input, long size, String contentType) {
        throw new UnsupportedOperationException("Gateway is read-only");
    }

    @Override
    public void deletePrefix(String prefix) {
        throw new UnsupportedOperationException("Gateway is read-only");
    }
}
