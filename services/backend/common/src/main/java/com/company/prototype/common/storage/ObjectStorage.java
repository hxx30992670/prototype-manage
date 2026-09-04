package com.company.prototype.common.storage;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

public interface ObjectStorage {

    record PresignedUpload(String uploadUrl, String objectKey, Instant expiresAt) {}

    record ObjectMetadata(String objectKey, long size, String checksum, String contentType) {}

    PresignedUpload createUpload(String objectKey, long maxBytes, Duration ttl);

    ObjectMetadata stat(String objectKey);

    InputStream open(String objectKey);

    void put(String objectKey, InputStream input, long size, String contentType);

    void deletePrefix(String prefix);
}
