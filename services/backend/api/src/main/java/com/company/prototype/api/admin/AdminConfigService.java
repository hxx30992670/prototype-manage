package com.company.prototype.api.admin;

import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import com.company.prototype.persistence.config.SystemConfigEntity;
import com.company.prototype.persistence.config.SystemConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置读写服务。
 *
 * 每个配置项在代码中声明默认值与硬上限：管理员只能调整默认值，不能越过硬上限。
 * 读取带进程内缓存；更新时清缓存并写审计。Worker/Gateway 为独立进程：
 * Worker 每次领取发布任务时重新读取本服务对数据库的查询路径（不跨进程缓存）；
 * Gateway 不消费业务配置。
 */
@Service
public class AdminConfigService {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigService.class);

    public enum ValueType { LONG, BOOLEAN, STRING }

    /** 配置项声明：key -> (默认值, 硬上限, 类型)。硬上限为 null 表示无上限（仅类型校验）。 */
    public record ConfigSpec(String key, String defaultValue, Long hardMaximum, ValueType type, String description) {}

    private static final List<ConfigSpec> SPECS = List.of(
        new ConfigSpec("upload.html.maxMb", "20", 100L, ValueType.LONG, "单个 HTML 文件大小上限（MB）"),
        new ConfigSpec("upload.zip.maxMb", "200", 1000L, ValueType.LONG, "ZIP 文件大小上限（MB）"),
        new ConfigSpec("upload.expanded.maxMb", "500", 2000L, ValueType.LONG, "ZIP 解压后总大小上限（MB）"),
        new ConfigSpec("upload.fileCount.max", "10000", 50000L, ValueType.LONG, "ZIP 文件数量上限"),
        new ConfigSpec("upload.directoryDepth.max", "20", 50L, ValueType.LONG, "ZIP 最大目录深度"),
        new ConfigSpec("upload.singleEntry.maxMb", "100", 200L, ValueType.LONG, "ZIP 单个条目大小上限（MB）"),
        new ConfigSpec("upload.compressionRatio.max", "100", 200L, ValueType.LONG, "ZIP 最大压缩比"),
        new ConfigSpec("upload.extractTimeout.seconds", "60", 120L, ValueType.LONG, "单次解压耗时上限（秒）"),
        new ConfigSpec("attachment.maxMb", "50", 200L, ValueType.LONG, "单附件大小上限（MB）"),
        new ConfigSpec("attachment.totalPerPrototypeGb", "1", 5L, ValueType.LONG, "单原型附件总大小上限（GB）"),
        new ConfigSpec("recycle.retentionDays", "30", 180L, ValueType.LONG, "回收站保留天数"),
        new ConfigSpec("captcha.enabled", "false", null, ValueType.BOOLEAN, "登录验证码开关"),
        new ConfigSpec("share.passwordFailureLimit", "5", 20L, ValueType.LONG, "分享密码错误阈值"),
        new ConfigSpec("share.passwordLockMinutes", "15", 120L, ValueType.LONG, "分享密码锁定分钟数")
    );

    private final SystemConfigRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public AdminConfigService(SystemConfigRepository repository, AuditLogRepository auditLogRepository) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
    }

    public static List<ConfigSpec> specs() {
        return SPECS;
    }

    /** 管理员视角：返回全部配置项（含默认值、当前值、硬上限、类型、说明）。 */
    @Transactional(readOnly = true)
    public List<AdminConfigDtos.ConfigItemResponse> listAll() {
        Map<String, String> current = loadAll();
        return SPECS.stream().map(spec -> {
            String value = current.getOrDefault(spec.key(), spec.defaultValue());
            return new AdminConfigDtos.ConfigItemResponse(spec.key(), value, spec.defaultValue(), spec.hardMaximum(), spec.type().name(), spec.description());
        }).toList();
    }

    /** 业务读取：获取配置项当前值；未设置时返回默认值。 */
    public String getValue(String key) {
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        ConfigSpec spec = findSpec(key);
        String value = repository.findByConfigKey(key)
            .map(SystemConfigEntity::getConfigValue)
            .orElse(spec.defaultValue());
        cache.put(key, value);
        return value;
    }

    public long getLong(String key) {
        return Long.parseLong(getValue(key));
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(getValue(key));
    }

    @Transactional
    public AdminConfigDtos.ConfigItemResponse update(String key, String value, Long actorId) {
        ConfigSpec spec = findSpec(key);
        validate(spec, value);
        Instant now = Instant.now();
        repository.save(new SystemConfigEntity(key, value, spec.type().name(), actorId, now));
        cache.put(key, value);

        AuditLogEntity audit = new AuditLogEntity();
        audit.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));
        audit.setActorType("USER");
        audit.setActorId(actorId);
        audit.setAction("SYSTEM_CONFIG_UPDATE");
        audit.setTargetType("SYSTEM_CONFIG");
        audit.setTargetId(key);
        audit.setResult("SUCCESS");
        audit.setSummary("更新系统配置: " + key + "=" + value);
        audit.setCreatedAt(now);
        auditLogRepository.save(audit);

        log.info("System config updated key={} actorId={}", key, actorId);
        return new AdminConfigDtos.ConfigItemResponse(key, value, spec.defaultValue(), spec.hardMaximum(), spec.type().name(), spec.description());
    }

    private void validate(ConfigSpec spec, String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "配置值不能为空");
        }
        if (spec.type() == ValueType.LONG) {
            long parsed;
            try {
                parsed = Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "配置值必须是整数");
            }
            if (parsed <= 0) {
                throw new ApiException(ApiErrorCode.CONFIG_OUT_OF_RANGE, "配置值必须大于 0");
            }
            if (spec.hardMaximum() != null && parsed > spec.hardMaximum()) {
                throw new ApiException(ApiErrorCode.CONFIG_OUT_OF_RANGE,
                    "配置值超出硬上限 " + spec.hardMaximum());
            }
        } else if (spec.type() == ValueType.BOOLEAN) {
            if (!"true".equalsIgnoreCase(value.trim()) && !"false".equalsIgnoreCase(value.trim())) {
                throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "配置值必须是 true 或 false");
            }
        }
    }

    private ConfigSpec findSpec(String key) {
        return SPECS.stream().filter(s -> s.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "未知的配置项: " + key));
    }

    private Map<String, String> loadAll() {
        Map<String, String> map = new LinkedHashMap<>();
        for (SystemConfigEntity e : repository.findAll()) {
            map.put(e.getConfigKey(), e.getConfigValue());
        }
        return map;
    }
}
