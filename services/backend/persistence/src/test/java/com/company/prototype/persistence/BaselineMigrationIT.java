package com.company.prototype.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class BaselineMigrationIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Test
    void baselineContainsPrototypeOwnershipAndCurrentVersionConstraints() throws Exception {
        Flyway.configure()
            .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        try (Connection connection = mysql.createConnection("")) {
            assertThat(columnNames(connection, "prototype"))
                .contains("public_id", "created_by", "owner_id", "current_version_id", "row_version");
            assertThat(columnNames(connection, "audit_log"))
                .contains("trace_id", "actor_id", "action", "result", "created_at");
            assertThat(columnNames(connection, "sys_user"))
                .contains("username", "password_hash", "must_change_password", "created_at", "updated_at");
            assertThat(tableNames(connection)).contains("prototype_owner_rel");
        }
    }

    private Set<String> tableNames(Connection connection) throws Exception {
        Set<String> tables = new HashSet<>();
        try (ResultSet rs = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }
        return tables;
    }

    private Set<String> columnNames(Connection connection, String tableName) throws Exception {
        Set<String> columns = new HashSet<>();
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }
}
