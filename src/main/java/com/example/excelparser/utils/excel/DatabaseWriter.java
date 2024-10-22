package com.example.excelparser.utils.excel;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import org.apache.nifi.logging.ComponentLog;
import org.apache.poi.ss.usermodel.Sheet;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Builder
public class DatabaseWriter {
    @NotNull
    private Connection connection;
    @NotNull
    private ExcelBookReader bookReader;
    @Builder.Default
    private String schemeName = "public";
    @Builder.Default
    private boolean overwrite = false;
    private ComponentLog logger;

    void logError(String msg) {
        if (Objects.nonNull(logger))
            logger.error(msg);
    }

    public void write(List<QueryPropertyHolder> queryPropertyHolders) {
        queryPropertyHolders.forEach(holder -> write(holder));
    }

    public void write(QueryPropertyHolder holder) {
        Sheet sheet = bookReader.getWorkbook().getSheet(holder.getSheetName());
        String tableName = holder.getDbTableName();
        // получение типов данных таблицы БД
        Map<String, String> postgresTypes = getPostgresTypes(sheet, holder);

        // проверка сущестовования таблицы БД
        final boolean exists = checkIfTableExists(connection, schemeName, tableName);
        if (!exists) {
            // создание таблицы БД
            String fieldsDefinition = toFieldsDefinition(postgresTypes);
            createTable(connection, schemeName, tableName, fieldsDefinition);
        }

        // очистка таблицы
        if (overwrite)
            truncateTable(connection, schemeName, tableName);

        // имена полей
        String fieldNames = toFieldNames(postgresTypes);
        // типы полей
        List<String> fieldTypes = toFieldTypes(postgresTypes);
        // значения полей
        String fieldValues = bookReader.toPostgresTableValues(sheet, fieldTypes, holder.getFirstDataRow());
        // заполнение таблицы
        insertData(connection, schemeName, tableName, fieldNames, fieldValues);
    }

    private Map<String, String> getPostgresTypes(Sheet sheet, QueryPropertyHolder holder) {
        return holder.getDbColumnNames().isEmpty()
                // имена и типы данных для postgres (по строке данных)
                ? bookReader.getPostgresTypes(sheet, holder.getFirstDataRow(), 0)
                // имена и типы данных для postgres (по списку имен столбцов)
                : bookReader.getPostgresTypes(sheet, holder.getFirstDataRow(), holder.getDbColumnNames());
    }

    private String toFieldsDefinition(Map<String, String> postgresTypes) {
        return postgresTypes.entrySet()
                .stream()
                .map(entry -> "%s %s".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private String toFieldNames(Map<String, String> postgresTypes) {
        return postgresTypes.entrySet()
                .stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));
    }

    private List<String> toFieldTypes(Map<String, String> postgresTypes) {
        return postgresTypes.entrySet()
                .stream()
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private boolean checkIfTableExists(final Connection connection,
                                       final String schemeName,
                                       final String tableName) {
        final String query = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_schema = '%s'
                            AND table_name = '%s'
                    );
                    """.formatted(schemeName, tableName);

//        try (var statement = connection.createStatement()) {
//            ResultSet resultSet = statement.executeQuery(query);
//            return (resultSet.next())
//                    ? resultSet.getBoolean(1)
//                    : false;
//        } catch (SQLException e) {
//            logError(e.getMessage());
//            return false;
//        }
        return true;
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

    private boolean truncateTable(final Connection connection,
                                  final String schemeName,
                                  final String tableName) {
        final String query = """
                TRUNCATE TABLE %s.%s;
                """.formatted(schemeName, tableName);

        return executeUpdate(connection, query);
    }

    public boolean insertData(final Connection connection,
                              final String schemeName,
                              final String tableName,
                              final String fieldNames,
                              final String fieldValues) {

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

    private boolean executeUpdate(Connection connection, String query) {
        System.out.println("Query:\n%s\n".formatted(query));

//        try (var statement = connection.createStatement()) {
//            statement.executeUpdate(query);
//            return true;
//        } catch (SQLException e) {
//            logError(e.getMessage());
//            return false;
//        }
        return true;
    }
}
