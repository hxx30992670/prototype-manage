package com.company.prototype.gateway;

import com.company.prototype.common.ticket.ContentTicketRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class ContentTicketVerifier {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ContentTicketVerifier(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ContentTicketRecord> verify(String rawTicket) {
        if (rawTicket == null || rawTicket.isBlank()) {
            return Optional.empty();
        }

        String digest = hashTicket(rawTicket);
        String json = redisTemplate.opsForValue().get("ticket:content:" + digest);
        // 兼容短期内已签发的旧分享票据；新票据统一使用 ticket:content 命名空间。
        if (json == null || json.isBlank()) {
            json = redisTemplate.opsForValue().get("content:ticket:" + digest);
        }
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }

        try {
            ContentTicketRecord record = objectMapper.readValue(json, ContentTicketRecord.class);
            if (record.expiresAt() != null && record.expiresAt().isBefore(Instant.now())) {
                return Optional.empty();
            }
            if ("SHARE".equalsIgnoreCase(record.authorizationType()) && !isShareTicketValid(record)) {
                return Optional.empty();
            }
            return Optional.of(record);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static String hashTicket(String rawTicket) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawTicket.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    private boolean isShareTicketValid(ContentTicketRecord record) {
        String sessionId = record.sessionId();
        long shareLinkId;

        if (sessionId != null && sessionId.startsWith("anonymous:")) {
            try {
                shareLinkId = Long.parseLong(sessionId.substring("anonymous:".length()));
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            if (sessionId == null || sessionId.isBlank()) {
                return false;
            }
            String sessionJson = redisTemplate.opsForValue().get("share:session:" + sessionId);
            if (sessionJson == null || sessionJson.isBlank()) {
                return false;
            }
            try {
                JsonNode session = objectMapper.readTree(sessionJson);
                shareLinkId = session.path("shareLinkId").asLong(Long.MIN_VALUE);
                if (shareLinkId == Long.MIN_VALUE
                    || session.path("authEpoch").asLong(Long.MIN_VALUE) != record.authEpoch()) {
                    return false;
                }
                String expiresAt = session.path("expiresAt").asText(null);
                if (expiresAt == null || Instant.parse(expiresAt).isBefore(Instant.now())) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }

        String currentEpoch = redisTemplate.opsForValue().get("share:auth-epoch:" + shareLinkId);
        try {
            return currentEpoch != null && Long.parseLong(currentEpoch) == record.authEpoch();
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
