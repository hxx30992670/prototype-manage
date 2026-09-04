package com.company.prototype.common.logging;

import java.util.regex.Pattern;

/**
 * 凭证脱敏器：在日志/审计写入前替换分享 Token、内容票据、下载票据、
 * 预签名查询参数等敏感路径段与键值对。所有运行模块必须使用同一实现。
 */
public final class CredentialRedactor {

    private static final String REDACTED = "<redacted>";

    /** 路径段形式的凭证：/s/{token}、/share-api/v1/shares/{token}、/content/c/{ticket}、下载票据路径。 */
    private static final Pattern PATH_CREDENTIAL = Pattern.compile(
        "(?i)(/s/|/share-api/v1/shares/|/content/c/|/api/v1/downloads/|/share-api/v1/downloads/)[A-Za-z0-9._~-]{8,}");

    /** 签名查询参数与常见敏感键值对。 */
    private static final Pattern SIGNATURE_QUERY = Pattern.compile(
        "(?i)([?&](X-Amz-Signature|X-Amz-Credential|X-Amz-Security-Token|signature|token|ticket|password|secret|credential)=)[^&#\\s]+");

    private static final Pattern KEY_VALUE = Pattern.compile(
        "(?i)(password|token|ticket|session|signature|credential|secret|csrf|authorization)[=: ]+[^\\s,;&\"']+");

    private static final Pattern BEARER = Pattern.compile(
        "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");

    private CredentialRedactor() {}

    /** 对 URI 或自由文本执行脱敏；null 原样返回。 */
    public static String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String safe = PATH_CREDENTIAL.matcher(text).replaceAll("$1" + REDACTED);
        safe = SIGNATURE_QUERY.matcher(safe).replaceAll("$1" + REDACTED);
        safe = BEARER.matcher(safe).replaceAll("Bearer=" + REDACTED);
        safe = KEY_VALUE.matcher(safe).replaceAll("$1=" + REDACTED);
        return safe;
    }
}
