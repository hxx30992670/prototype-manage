package com.company.prototype.gateway;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PreviewSecurityHeaders {

    private final String previewDomain;
    private final String prototypeDomain;

    public PreviewSecurityHeaders(
        @Value("${preview.domain:http://preview.corp.test}") String previewDomain,
        @Value("${prototype.domain:http://prototype.corp.test}") String prototypeDomain
    ) {
        this.previewDomain = previewDomain;
        this.prototypeDomain = prototypeDomain;
    }

    public void apply(HttpServletResponse response, boolean isHtml) {
        if (isHtml) {
            String csp = "default-src 'none'; "
                + "script-src " + previewDomain + " 'unsafe-inline' 'unsafe-eval'; "
                + "style-src " + previewDomain + " 'unsafe-inline'; "
                + "img-src " + previewDomain + " data: blob:; "
                + "font-src " + previewDomain + " data:; "
                + "media-src " + previewDomain + " blob:; "
                + "connect-src " + previewDomain + "; "
                + "frame-src " + previewDomain + "; "
                + "worker-src " + previewDomain + " blob:; "
                + "object-src 'none'; "
                + "base-uri 'none'; "
                + "form-action " + previewDomain + "; "
                + "frame-ancestors " + prototypeDomain + " " + previewDomain + "; "
                + "sandbox allow-scripts allow-forms allow-modals allow-downloads";
            response.setHeader("Content-Security-Policy", csp);
        }

        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
