package com.company.prototype.common.logging;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialRedactorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "/s/RAW_SHARE_TOKEN",
        "/share-api/v1/shares/RAW_SHARE_TOKEN/comments",
        "/content/c/RAW_CONTENT_TICKET/index.html",
        "/api/v1/downloads/RAW_DOWNLOAD_TICKET",
        "/share-api/v1/downloads/RAW_DOWNLOAD_TICKET?X-Amz-Signature=SECRET",
        "/upload-objects/prototype-objects/temporary/abc/source?X-Amz-Signature=SECRET&X-Amz-Credential=CRED",
        "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.tokenvalue",
        "password=SuperSecret123 分享链接=/s/RAW_SHARE_TOKEN"
    })
    void redactsCredentialsFromUri(String uri) {
        String safe = CredentialRedactor.redact(uri);
        assertThat(safe).doesNotContain("RAW_", "SECRET", "tokenvalue", "SuperSecret123",
            "X-Amz-Signature=SECRET", "X-Amz-Credential=CRED");
        assertThat(safe).contains("<redacted>");
    }

    @org.junit.jupiter.api.Test
    void redactKeepsNormalContent() {
        assertThat(CredentialRedactor.redact("/api/v1/prototypes?page=1&pageSize=20"))
            .isEqualTo("/api/v1/prototypes?page=1&pageSize=20");
        assertThat(CredentialRedactor.redact(null)).isNull();
        assertThat(CredentialRedactor.redact("")).isEmpty();
    }
}
