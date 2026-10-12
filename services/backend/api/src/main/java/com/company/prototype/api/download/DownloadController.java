package com.company.prototype.api.download;

import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.common.storage.ObjectStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
public class DownloadController {

    private final StringRedisTemplate redisTemplate;
    private final ObjectStorage objectStorage;
    private final DownloadAuthorizationService downloadAuthorizationService;

    public DownloadController(
        StringRedisTemplate redisTemplate,
        ObjectStorage objectStorage,
        DownloadAuthorizationService downloadAuthorizationService
    ) {
        this.redisTemplate = redisTemplate;
        this.objectStorage = objectStorage;
        this.downloadAuthorizationService = downloadAuthorizationService;
    }

    @GetMapping({"/api/v1/downloads/{ticket}", "/share-api/v1/downloads/{ticket}"})
    public void download(
        @PathVariable("ticket") String ticket,
        CurrentUser user,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws Exception {
        String key = "download:ticket:" + ticket;
        String val = redisTemplate.opsForValue().get(key);
        if (val == null || val.isBlank()) {
            throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "下载链接已失效或不存在");
        }

        redisTemplate.delete(key);

        DownloadAuthorizationService.DownloadFile file;
        if (request.getRequestURI().startsWith("/share-api/")) {
            file = resolveShareDownload(val);
        } else {
            DownloadTicketRecord record = DownloadTicketRecord.parse(val)
                .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "下载链接已失效或不存在"));
            file = downloadAuthorizationService.authorize(record, user);
        }

        ObjectStorage.ObjectMetadata meta = objectStorage.stat(file.objectKey());
        if (meta == null) {
            throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "存储文件不存在");
        }

        response.setContentType("application/octet-stream");
        response.setContentLengthLong(meta.size());
        String encodedFilename = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        try (InputStream in = objectStorage.open(file.objectKey())) {
            in.transferTo(response.getOutputStream());
        }
    }

    private DownloadAuthorizationService.DownloadFile resolveShareDownload(String value) {
        if (value.startsWith("internal-v1|")) {
            throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "下载链接已失效或不存在");
        }
        String[] parts = value.split("\\|", 2);
        return new DownloadAuthorizationService.DownloadFile(parts[0], parts.length > 1 ? parts[1] : "prototype-source.zip");
    }
}
