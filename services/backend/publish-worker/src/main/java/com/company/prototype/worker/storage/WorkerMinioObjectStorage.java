package com.company.prototype.worker.storage;

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
public class WorkerMinioObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(WorkerMinioObjectStorage.class);

    private final String endpoint;
    private final String bucket;
    private final String rootUser;
    private final String rootPassword;

    private MinioClient client;

    public WorkerMinioObjectStorage(
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
            log.warn("Failed to initialize MinIO client in worker: {}", e.getMessage());
        }
    }

    @Override
    public PresignedUpload createUpload(String objectKey, long maxBytes, Duration ttl) {
        throw new UnsupportedOperationException("Worker does not generate presigned upload URLs");
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
            throw new RuntimeException("Failed to delete objects with prefix " + prefix + ": " + e.getMessage(), e);
        }
    }
}
