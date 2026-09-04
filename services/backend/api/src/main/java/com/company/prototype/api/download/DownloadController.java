package com.company.prototype.api.download;

import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.common.storage.ObjectStorage;
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

    public DownloadController(StringRedisTemplate redisTemplate, ObjectStorage objectStorage) {
        this.redisTemplate = redisTemplate;
        this.objectStorage = objectStorage;
    }

    @GetMapping({"/api/v1/downloads/{ticket}", "/share-api/v1/downloads/{ticket}"})
    public void download(@PathVariable("ticket") String ticket, HttpServletResponse response) throws Exception {
        String key = "download:ticket:" + ticket;
        String val = redisTemplate.opsForValue().get(key);
        if (val == null || val.isBlank()) {
            throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "下载链接已失效或不存在");
        }

        redisTemplate.delete(key);

        String[] parts = val.split("\\|", 2);
        String objectKey = parts[0];
        String filename = parts.length > 1 ? parts[1] : "prototype-source.zip";

        ObjectStorage.ObjectMetadata meta = objectStorage.stat(objectKey);
        if (meta == null) {
            throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "存储文件不存在");
        }

        response.setContentType("application/octet-stream");
        response.setContentLengthLong(meta.size());
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        try (InputStream in = objectStorage.open(objectKey)) {
            in.transferTo(response.getOutputStream());
        }
    }
}
