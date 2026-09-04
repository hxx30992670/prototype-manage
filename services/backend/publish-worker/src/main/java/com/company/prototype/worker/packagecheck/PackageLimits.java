package com.company.prototype.worker.packagecheck;

public record PackageLimits(
    long maxSingleEntryBytes,
    long maxTotalExpandedBytes,
    int maxFileCount,
    int maxDirectoryDepth,
    double maxCompressionRatio,
    long timeoutSeconds
) {
    public static PackageLimits defaultLimits() {
        return new PackageLimits(
            50 * 1024 * 1024L,   // 50MB 单条目限制
            500 * 1024 * 1024L,  // 500MB 总解压大小限制
            10000,               // 10000 文件数限制
            20,                  // 20 层目录深度
            100.0,               // 100倍压缩比限制
            120                  // 120秒超时限制
        );
    }
}
