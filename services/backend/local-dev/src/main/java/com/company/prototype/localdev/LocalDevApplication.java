package com.company.prototype.localdev;

import com.company.prototype.api.PrototypeApiApplication;
import com.company.prototype.gateway.PreviewGatewayApplication;
import com.company.prototype.worker.PublishWorkerApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * IDEA 本地开发唯一启动入口。
 *
 * <p>生产环境仍以独立容器运行三个服务；本类只在开发机内以独立 Spring Context
 * 组合 API、发布 Worker 与预览网关，保留服务间的端口和安全边界。</p>
 */
public final class LocalDevApplication {

    static final Map<String, Object> API_PROPERTIES = Map.of(
        "server.port", "18080",
        "management.server.port", "18081",
        "spring.application.name", "prototype-api"
    );

    static final Map<String, Object> WORKER_PROPERTIES = Map.of(
        "spring.main.web-application-type", "none",
        "spring.application.name", "prototype-publish-worker"
    );

    static final Map<String, Object> GATEWAY_PROPERTIES = Map.of(
        "server.port", "18082",
        "management.server.port", "18083",
        "spring.application.name", "preview-gateway",
        "spring.autoconfigure.exclude", String.join(",",
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
        )
    );

    private LocalDevApplication() {
    }

    public static void main(String[] args) {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        List<ConfigurableApplicationContext> contexts = List.of(
            run(PrototypeApiApplication.class, defaultProperties(workingDirectory, API_PROPERTIES), args),
            run(PublishWorkerApplication.class, defaultProperties(workingDirectory, WORKER_PROPERTIES), args),
            run(PreviewGatewayApplication.class, defaultProperties(workingDirectory, GATEWAY_PROPERTIES), args)
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (int index = contexts.size() - 1; index >= 0; index--) {
                contexts.get(index).close();
            }
        }, "prototype-local-dev-shutdown"));
    }

    static Map<String, Object> defaultProperties(Path workingDirectory, Map<String, Object> serviceDefaults) {
        Map<String, Object> defaults = new HashMap<>();
        Properties dotenv = LocalDevEnvironment.load(workingDirectory);
        dotenv.forEach((key, value) -> defaults.put(String.valueOf(key), value));

        String mysqlDatabase = valueOf(dotenv, "MYSQL_DATABASE", "prototype_dev");
        defaults.put("spring.datasource.url", valueOf(
                dotenv,
                "SPRING_DATASOURCE_URL",
                "jdbc:mysql://127.0.0.1:13306/" + mysqlDatabase
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
        ));
        putIfPresent(defaults, "spring.datasource.username", dotenv.getProperty("MYSQL_USER"));
        putIfPresent(defaults, "spring.datasource.password", dotenv.getProperty("MYSQL_PASSWORD"));
        putIfPresent(defaults, "spring.datasource.username", dotenv.getProperty("SPRING_DATASOURCE_USERNAME"));
        putIfPresent(defaults, "spring.datasource.password", dotenv.getProperty("SPRING_DATASOURCE_PASSWORD"));

        defaults.put("spring.data.redis.host", valueOf(dotenv, "REDIS_HOST", "127.0.0.1"));
        defaults.put("spring.data.redis.port", valueOf(dotenv, "REDIS_PORT", "16379"));
        defaults.put("spring.data.redis.password", valueOf(dotenv, "REDIS_PASSWORD", ""));

        defaults.put("minio.endpoint", valueOf(dotenv, "MINIO_ENDPOINT", "http://127.0.0.1:19000"));
        putIfPresent(defaults, "minio.bucket", dotenv.getProperty("MINIO_BUCKET"));
        putIfPresent(defaults, "minio.root-user", dotenv.getProperty("MINIO_ROOT_USER"));
        putIfPresent(defaults, "minio.root-password", dotenv.getProperty("MINIO_ROOT_PASSWORD"));
        putIfPresent(defaults, "minio.upload-public-base-url", dotenv.getProperty("UPLOAD_PUBLIC_BASE_URL"));

        defaults.put("server.servlet.session.cookie.secure", valueOf(dotenv, "SESSION_COOKIE_SECURE", "false"));
        String previewBaseUrl = valueOf(
                dotenv,
                "PREVIEW_DOMAIN",
                valueOf(dotenv, "PREVIEW_BASE_URL", "http://preview.localhost:18000")
        );
        defaults.put("preview.domain", previewBaseUrl);
        defaults.put("preview.base-url", previewBaseUrl);
        defaults.put("prototype.domain", valueOf(
                dotenv,
                "PROTOTYPE_DOMAIN",
                valueOf(dotenv, "MANAGEMENT_BASE_URL", "http://prototype.localhost:18000")
        ));
        defaults.putAll(serviceDefaults);
        return defaults;
    }

    private static String valueOf(Properties dotenv, String key, String defaultValue) {
        return dotenv.getProperty(key, defaultValue);
    }

    private static void putIfPresent(Map<String, Object> properties, String key, String value) {
        if (value != null) {
            properties.put(key, value);
        }
    }

    private static ConfigurableApplicationContext run(
        Class<?> source,
        Map<String, Object> defaults,
        String[] args
    ) {
        List<String> commandLineArgs = new ArrayList<>(args.length);
        Collections.addAll(commandLineArgs, args);
        SpringApplication application = new SpringApplication(source);
        configure(application, defaults);
        return application.run(commandLineArgs.toArray(String[]::new));
    }

    /**
     * {@code spring.main.*} is bound during environment preparation, before
     * context initializers run. Default properties make {@code web-application-type=none}
     * take effect; the initializer still overrides {@code application.yml} ports
     * on the shared local-dev classpath.
     */
    static void configure(SpringApplication application, Map<String, Object> defaults) {
        application.setDefaultProperties(new HashMap<>(defaults));
        application.addInitializers(context -> addLocalPropertySource(context.getEnvironment(), defaults));
    }

    static void addLocalPropertySource(ConfigurableEnvironment environment, Map<String, ?> localProperties) {
        Map<String, Object> values = new HashMap<>();
        localProperties.forEach(values::put);
        environment.getPropertySources().addAfter(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new MapPropertySource("localDevDotenv", values)
        );
    }
}
