package com.company.prototype.gateway;

import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.common.ticket.ContentTicketRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

@RestController
@RequestMapping("/content/c/{ticket}")
public class ContentController {

    private static final Logger log = LoggerFactory.getLogger(ContentController.class);

    private final ContentTicketVerifier ticketVerifier;
    private final PreviewSecurityHeaders securityHeaders;
    private final ObjectStorage objectStorage;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public ContentController(
        ContentTicketVerifier ticketVerifier,
        PreviewSecurityHeaders securityHeaders,
        ObjectStorage objectStorage,
        @org.springframework.beans.factory.annotation.Autowired(required = false)
            io.micrometer.core.instrument.MeterRegistry meterRegistry
    ) {
        this.ticketVerifier = ticketVerifier;
        this.securityHeaders = securityHeaders;
        this.objectStorage = objectStorage;
        this.meterRegistry = meterRegistry;
    }

    private void countTicketReject() {
        if (meterRegistry != null) {
            meterRegistry.counter("prototype_ticket_rejects_total").increment();
        }
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.HEAD})
    public void serveRoot(
        @PathVariable("ticket") String ticket,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws Exception {
        serveAsset(ticket, "", request, response);
    }

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void serveSubPath(
        @PathVariable("ticket") String ticket,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws Exception {
        String uri = request.getRequestURI();
        String prefix = "/content/c/" + ticket;
        String subpath = "";
        if (uri.startsWith(prefix)) {
            subpath = uri.substring(prefix.length());
        }
        serveAsset(ticket, subpath, request, response);
    }

    private void serveAsset(
        String ticket,
        String rawPath,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws Exception {
        var recordOpt = ticketVerifier.verify(ticket);
        if (recordOpt.isEmpty()) {
            countTicketReject();
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        ContentTicketRecord record = recordOpt.get();

        String subpath;
        try {
            subpath = SafeContentPath.normalize(rawPath);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (subpath.isBlank() || subpath.equals("/")) {
            subpath = record.entryPath() != null && !record.entryPath().isBlank()
                ? record.entryPath()
                : "index.html";
        }

        String fullKey = record.publishPrefix();
        if (!fullKey.endsWith("/") && !subpath.startsWith("/")) {
            fullKey = fullKey + "/";
        }
        fullKey = fullKey + subpath;

        ObjectStorage.ObjectMetadata meta = objectStorage.stat(fullKey);
        if (meta == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String contentType = determineContentType(subpath);
        boolean isHtml = contentType.startsWith("text/html");

        securityHeaders.apply(response, isHtml);
        response.setContentType(contentType);
        response.setContentLengthLong(meta.size());

        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            return;
        }

        try (InputStream in = objectStorage.open(fullKey); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    private String determineContentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html; charset=UTF-8";
        }
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
            return "application/javascript; charset=UTF-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (lower.endsWith(".json") || lower.endsWith(".map")) {
            return "application/json; charset=UTF-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".wasm")) {
            return "application/wasm";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (lower.endsWith(".woff")) {
            return "font/woff";
        }
        if (lower.endsWith(".ttf")) {
            return "font/ttf";
        }
        if (lower.endsWith(".otf")) {
            return "font/otf";
        }
        if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain; charset=UTF-8";
        }
        return "application/octet-stream";
    }
}
