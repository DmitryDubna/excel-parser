package com.example.excelparser.utils.database;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.apache.nifi.logging.ComponentLog;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
public class PostgresQueryService {
    @NonNull
    private Connection connection;
    @NonNull
    private String schemeName;
    @NonNull
    private String tableName;
//    @NonNull
    private ComponentLog logger;
    
    private void debug(final String msg) {
        System.out.println("[debug]: " + msg);
    }

    private void error(final String msg) {
        System.out.println("[error]: " + msg);
    }

    public boolean tryCreateTable(Map<String, String> postgresTypes) {
        debug("Проверка сущестовования таблицы БД");
        // проверка сущестовования таблицы БД
        final boolean exists = checkIfTableExists(connection, schemeName, tableName);
        debug("Таблица БД существует: " + exists);
        if (exists)
            return true;

        // создание таблицы БД
        String fieldsDefinition = toFieldsDefinition(postgresTypes);
        debug("Создание таблицы БД");
        boolean success = createTable(connection, schemeName, tableName, fieldsDefinition);
        debug("Таблица БД создана: " + success);

        return success;
    }

    public static String toFieldNames(Map<String, String> postgresTypes) {
        return postgresTypes.keySet()
                .stream()
                .collect(Collectors.joining(", "));
    }

    public static List<String> toFieldTypes(Map<String, String> postgresTypes) {
        return postgresTypes.values()
                .stream()
                .collect(Collectors.toList());
    }

    private String toFieldsDefinition(Map<String, String> postgresTypes) {
        return postgresTypes.entrySet()
                .stream()
                .map(entry -> "%s %s".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private boolean executeUpdate(Connection connection, String query) {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(query);
            return true;
        } catch (SQLException e) {
            error(e.getMessage());
            // FIXME: убрать после тестирования
            debug("Query:\n" + query.substring(0, Math.min(10000, query.length() - 1)));
            return false;
        }
    }

    private boolean checkIfTableExists(final Connection connection,
                                       final String schemeName,
                                       final String tableName) {
        final String query = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_schema ILIKE '%s'
                            AND table_name ILIKE '%s'
                    );
                    """.formatted(schemeName, tableName);

        try (var statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(query);
            return resultSet.next() && resultSet.getBoolean("exists");
        } catch (SQLException e) {
            error(e.getMessage());
            return false;
        }
    }

    private boolean createTable(final Connection connection,
                                final String schemeName,
                                final String tableName,
                                final String fieldsDefinition) {
        final String query = """
                CREATE TABLE %s.%s (%s);
                """.formatted(schemeName, tableName, fieldsDefinition);

        return executeUpdate(connection, query);
    }

    public boolean truncateTable() {
        final String query = """
                TRUNCATE TABLE %s.%s;
                """.formatted(schemeName, tableName);

        return executeUpdate(connection, query);
    }

    public boolean insertData(final String fieldNames, final String fieldValues) {

        final String query = """
                INSERT INTO %s.%s (%s)
                VALUES %s;
                """.formatted(
                        schemeName,
                        tableName,
                        fieldNames,
                        fieldValues
                );

        return executeUpdate(connection, query);
    }
}
