package com.company.prototype.api.storage;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * AWS SigV4 PUT 预签名 URL 生成器。
 *
 * MinIO Java SDK 的 endpoint 不允许带路径，且其生成的 PUT 预签名 URL 未携带
 * payload hash，浏览器直传会因 X-Amz-Content-Sha256 缺失被拒绝。因此这里手动
 * 实现 SigV4 预签名：把 payload hash 固定为 UNSIGNED-PAYLOAD 并放入 query，
 * 客户端无需任何额外签名头即可直传（Nginx /upload-objects/ 剥前缀后 MinIO
 * 收到的路径与签名路径一致）。
 */
public final class PresignedUrlSigner {

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd")
        .withZone(ZoneOffset.UTC);
    private static final String REGION = "us-east-1";
    private static final String SERVICE = "s3";
    private static final String PAYLOAD_HASH = "UNSIGNED-PAYLOAD";

    private PresignedUrlSigner() {}

    public static String signPutUrl(
        String origin,
        String bucket,
        String objectKey,
        String accessKey,
        String secretKey,
        Duration ttl
    ) {
        URI publicUri = URI.create(origin);
        String host = publicUri.getHost();
        int port = publicUri.getPort();
        if (port > 0 && port != 80 && port != 443) {
            host = host + ":" + port;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        long expires = Math.max(1, ttl.toSeconds());

        String credentialScope = dateStamp + "/" + REGION + "/" + SERVICE + "/aws4_request";
        String credential = URLEncoder.encode(accessKey + "/" + credentialScope, StandardCharsets.UTF_8);

        String canonicalUri = canonicalUri(bucket, objectKey);
        String canonicalQuery = String.join("&",
            "X-Amz-Algorithm=AWS4-HMAC-SHA256",
            "X-Amz-Content-Sha256=" + PAYLOAD_HASH,
            "X-Amz-Credential=" + credential,
            "X-Amz-Date=" + amzDate,
            "X-Amz-Expires=" + expires,
            "X-Amz-SignedHeaders=host"
        );

        String canonicalHeaders = "host:" + host + "\n";
        String canonicalRequest = String.join("\n",
            "PUT", canonicalUri, canonicalQuery, canonicalHeaders, "host", PAYLOAD_HASH);

        String stringToSign = String.join("\n",
            "AWS4-HMAC-SHA256", amzDate, credentialScope, hex(sha256(canonicalRequest)));

        byte[] signingKey = signingKey(secretKey, dateStamp);
        String signature = hex(hmac(signingKey, stringToSign));

        String publicPrefix = publicUri.getRawPath();
        if (publicPrefix == null || publicPrefix.equals("/")) {
            publicPrefix = "";
        } else if (publicPrefix.endsWith("/")) {
            publicPrefix = publicPrefix.substring(0, publicPrefix.length() - 1);
        }
        String publicOrigin = publicUri.getScheme() + "://" + publicUri.getAuthority();
        return publicOrigin + publicPrefix + canonicalUri + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
    }

    private static String canonicalUri(String bucket, String objectKey) {
        // 每段分别 URL 编码（/ 是路径分隔符）
        StringBuilder sb = new StringBuilder("/");
        sb.append(encodeSegment(bucket));
        for (String seg : objectKey.split("/")) {
            sb.append('/').append(encodeSegment(seg));
        }
        return sb.toString();
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%2F", "/");
    }

    private static byte[] signingKey(String secretKey, String dateStamp) {
        byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmac(kDate, REGION);
        byte[] kService = hmac(kRegion, SERVICE);
        return hmac(kService, "aws4_request");
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
