package com.company.prototype.common.error;

public enum ApiErrorCode {
    // Auth & User
    AUTH_INVALID_CREDENTIALS(401, "用户名或密码错误"),
    AUTH_ACCOUNT_DISABLED(403, "账号已被禁用，请联系管理员"),
    AUTH_RATE_LIMITED(429, "登录失败次数过多，请稍后再试"),
    PASSWORD_CHANGE_REQUIRED(403, "首次登录或重置密码后必须修改密码"),
    CAPTCHA_REQUIRED(400, "请输入验证码"),
    CAPTCHA_INVALID(400, "验证码错误或已过期"),
    ACCESS_DENIED(403, "无权访问此资源"),
    UNAUTHORIZED(401, "未登录或会话已过期"),
    CSRF_TOKEN_INVALID(403, "CSRF Token 无效或已过期"),

    // Resource & Common
    RESOURCE_NOT_FOUND(404, "请求的资源不存在"),
    RESOURCE_VERSION_CONFLICT(409, "资源版本冲突，已被其他操作更新，请刷新重试"),
    VALIDATION_FAILED(400, "输入数据校验失败"),
    BAD_REQUEST(400, "请求参数错误"),
    INTERNAL_SERVER_ERROR(500, "系统内部异常，请稍后重试"),

    // Prototype
    PROTOTYPE_CODE_EXISTS(409, "原型编码已存在"),
    PROTOTYPE_ARCHIVED(409, "原型已归档，不可修改"),
    PROTOTYPE_PUBLISH_IN_PROGRESS(409, "当前原型有正在进行的发布任务，禁止并发发布或回滚"),
    IDEMPOTENCY_KEY_REUSED(409, "幂等键已使用且请求内容不一致"),

    // Upload & Publish
    UPLOAD_OBJECT_MISSING(400, "上传对象不存在"),
    UPLOAD_SIZE_MISMATCH(400, "上传文件大小不匹配"),
    UPLOAD_CHECKSUM_MISMATCH(400, "文件校验和不匹配"),
    UPLOAD_EXPIRED(400, "上传会话已过期"),
    UPLOAD_TYPE_UNSUPPORTED(400, "不支持的文件类型"),
    UPLOAD_HTML_TOO_LARGE(400, "HTML 文件大小超过限制"),
    UPLOAD_ZIP_TOO_LARGE(400, "ZIP 文件大小超过限制"),
    ZIP_EXPANDED_SIZE_LIMIT(400, "ZIP 解压后总大小超过限制"),
    ZIP_FILE_COUNT_LIMIT(400, "ZIP 内文件数量超过限制"),
    ZIP_DIRECTORY_DEPTH_LIMIT(400, "ZIP 目录层级超过限制"),
    ZIP_ENTRY_SIZE_LIMIT(400, "单个文件解压大小超过限制"),
    ZIP_COMPRESSION_RATIO_LIMIT(400, "ZIP 压缩比异常"),
    ZIP_EXTRACT_TIMEOUT(400, "ZIP 解压处理超时"),
    ZIP_SLIP_ATTEMPT(400, "ZIP 条目路径不安全"),
    ZIP_ENTRY_INVALID(400, "ZIP 中未找到 index.html 入口"),
    PROTOTYPE_VERSION_PUBLISH_FAILED(400, "原型包发布失败，请查看校验报告"),
    ATTACHMENT_SIZE_LIMIT(400, "附件大小超过限制"),

    // Share
    SHARE_LINK_EXPIRED(403, "分享链接已过期"),
    SHARE_LINK_DISABLED(403, "分享链接已停用"),
    SHARE_LINK_NOT_FOUND(404, "分享链接不存在"),
    SHARE_PASSWORD_REQUIRED(403, "该分享需要密码访问"),
    SHARE_PASSWORD_INCORRECT(403, "分享密码错误"),
    SHARE_PASSWORD_RATE_LIMITED(429, "密码连续错误次数过多，已被临时锁定"),
    PREVIEW_TICKET_INVALID(403, "预览票据无效或已过期"),

    // Admin & Config
    CONFIG_OUT_OF_RANGE(400, "配置值超出允许的范围"),

    // Rate Limit
    RATE_LIMITED(429, "操作过于频繁，请稍后再试"),
    COMMENT_RATE_LIMITED(429, "发表评论过于频繁，请稍候再试");

    private final int httpStatus;
    private final String defaultMessage;

    ApiErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
