package com.company.prototype.localdev;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDevEnvironmentTest {

    @TempDir
    Path tempDir;

    @Test
    void findsDotenvInAnAncestorAndParsesValues() throws IOException {
        Path repository = tempDir.resolve("repository");
        Path workingDirectory = repository.resolve("services/backend");
        Files.createDirectories(workingDirectory);
        Files.writeString(repository.resolve(".env.dev"), "# local settings\nMINIO_ENDPOINT=http://127.0.0.1:19000\nEMPTY=\n");

        Properties properties = LocalDevEnvironment.load(workingDirectory);

        assertEquals("http://127.0.0.1:19000", properties.getProperty("MINIO_ENDPOINT"));
        assertEquals("", properties.getProperty("EMPTY"));
    }

    @Test
    void failsWithSetupInstructionWhenDotenvIsMissing() {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> LocalDevEnvironment.load(tempDir)
        );

        assertTrue(exception.getMessage().contains("cp -n .env.dev.example .env.dev"));
    }

    @Test
    void launcherDefaultsContainDotenvAndServiceSpecificValues() throws IOException {
        Path repository = tempDir.resolve("repository");
        Path workingDirectory = repository.resolve("services/backend");
        Files.createDirectories(workingDirectory);
        Files.writeString(repository.resolve(".env.dev"), "MYSQL_DATABASE=prototype_dev\nMYSQL_USER=prototype_dev_user\nMYSQL_PASSWORD=local-password\nMINIO_ROOT_USER=minio_dev_admin\nMINIO_ROOT_PASSWORD=local-minio-password\n");

        Map<String, Object> defaults = LocalDevApplication.defaultProperties(
            workingDirectory,
            Map.of("server.port", "18080")
        );

        assertEquals(
            "jdbc:mysql://127.0.0.1:13306/prototype_dev?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
            defaults.get("spring.datasource.url")
        );
        assertEquals("prototype_dev_user", defaults.get("spring.datasource.username"));
        assertEquals("local-password", defaults.get("spring.datasource.password"));
        assertEquals("http://127.0.0.1:19000", defaults.get("minio.endpoint"));
        assertEquals("minio_dev_admin", defaults.get("minio.root-user"));
        assertEquals("local-minio-password", defaults.get("minio.root-password"));
        assertEquals("http://prototype.localhost:18000", defaults.get("prototype.domain"));
        assertEquals("http://preview.localhost:18000", defaults.get("preview.domain"));
        assertEquals("http://preview.localhost:18000", defaults.get("preview.base-url"));
        assertEquals("18080", defaults.get("server.port"));
    }

    @Test
    void localPropertiesOverrideApplicationConfiguration() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addAfter(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new MapPropertySource("applicationConfig", Map.of("server.port", "8080"))
        );

        LocalDevApplication.addLocalPropertySource(environment, Map.of("server.port", "18080"));

        assertEquals("18080", environment.getProperty("server.port"));
    }

    @Test
    void workerDefaultsDisableTheWebServerOnTheSharedClasspath() throws IOException {
        Path repository = tempDir.resolve("repository");
        Path workingDirectory = repository.resolve("services/backend");
        Files.createDirectories(workingDirectory);
        Files.writeString(repository.resolve(".env.dev"), "MYSQL_DATABASE=prototype_dev\n");

        Map<String, Object> defaults = LocalDevApplication.defaultProperties(
            workingDirectory,
            LocalDevApplication.WORKER_PROPERTIES
        );

        assertEquals("none", defaults.get("spring.main.web-application-type"));
        assertEquals("prototype-publish-worker", defaults.get("spring.application.name"));
    }

    @Test
    void gatewayDefaultsDisableSecurityAutoConfigurationFromTheSharedClasspath() {
        Object exclude = LocalDevApplication.GATEWAY_PROPERTIES.get("spring.autoconfigure.exclude");
        assertTrue(String.valueOf(exclude).contains("SecurityAutoConfiguration"));
        assertTrue(String.valueOf(exclude).contains("ServletWebSecurityAutoConfiguration"));
    }

    @Test
    void launcherAppliesSpringMainPropertiesBeforeContextInitialization() throws Exception {
        SpringApplication application = new SpringApplication(Object.class);
        Map<String, Object> defaults = Map.of("spring.main.web-application-type", "none");

        LocalDevApplication.configure(application, defaults);

        Field field = SpringApplication.class.getDeclaredField("defaultProperties");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> actual = (Map<String, Object>) field.get(application);
        assertEquals("none", actual.get("spring.main.web-application-type"));
    }
}
