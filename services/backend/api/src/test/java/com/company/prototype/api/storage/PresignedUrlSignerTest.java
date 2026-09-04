package com.company.prototype.api.storage;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PresignedUrlSignerTest {

    @Test
    void keepsReverseProxyPrefixInBrowserUploadUrl() {
        String url = PresignedUrlSigner.signPutUrl(
            "http://prototype.local.test/upload-objects",
            "prototype-objects",
            "temporary/01/source",
            "minio_dev_admin",
            "secret",
            Duration.ofMinutes(10)
        );

        assertTrue(url.startsWith(
            "http://prototype.local.test/upload-objects/prototype-objects/temporary/01/source?"
        ));
    }
}
