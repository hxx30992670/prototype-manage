package com.company.prototype.localdev;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class LocalDevEnvironment {

    private static final String DOTENV_FILE_NAME = ".env.dev";

    private LocalDevEnvironment() {
    }

    static Properties load(Path workingDirectory) {
        Path dotenvFile = findDotenv(workingDirectory);
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(dotenvFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取本地环境文件: " + dotenvFile, exception);
        }
    }

    private static Path findDotenv(Path workingDirectory) {
        for (Path directory = workingDirectory.toAbsolutePath().normalize(); directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve(DOTENV_FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
            "未找到 .env.dev。请在仓库根目录执行: cp -n .env.dev.example .env.dev"
        );
    }
}
