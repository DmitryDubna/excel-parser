package com.example.excelparser.utils.excel;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.nifi.logging.ComponentLog;
import org.apache.poi.ss.usermodel.Sheet;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DatabaseWriter {
//    @NonNull
    private Connection connection;
    @NonNull
    private ExcelBookReader bookReader;
    private String schemeName;
    private boolean overwrite;
    private ComponentLog logger;


    public static DatabaseWriterBuilder builder() {
        return new DatabaseWriterBuilder();
    }

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

        if (postgresTypes.isEmpty()) {
            logError("Не удалось сформировать список типов данных.");
            return;
        }

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
        // значения полей
        String fieldValues = toFieldValues(sheet, postgresTypes, holder);
        // заполнение таблицы
        insertData(connection, schemeName, tableName, fieldNames, fieldValues);
    }

    private String toFieldValues(Sheet sheet, Map<String, String> postgresTypes, QueryPropertyHolder holder) {
        // типы полей
        List<String> fieldTypes = toFieldTypes(postgresTypes);
        // значения полей
        if (holder.getDataColumnInfo().isEmpty())
            return bookReader.toPostgresTableValues(sheet, fieldTypes, holder.getFirstDataRow());

        QueryPropertyHolder.DataColumnInfo columnInfo = holder.getDataColumnInfo().get();
        return bookReader.toPostgresTableValues(
                sheet,
                fieldTypes,
                holder.getFirstDataRow(),
                holder.getLastDataRow(),
                // FIXME
                Optional.of(columnInfo.getFrom()),
                Optional.of(columnInfo.getTo()));
    }

    private Map<String, String> getPostgresTypes(Sheet sheet, QueryPropertyHolder holder) {
        QueryPropertyHolder.DataColumnInfo columnInfo = holder.getDataColumnInfo().orElse(null);

        Optional<Integer> columnFrom = Objects.nonNull(columnInfo)
                ? Optional.of(columnInfo.getFrom())
                : Optional.empty();
        Optional<Integer> columnTo = Objects.nonNull(columnInfo)
                ? Optional.of(columnInfo.getTo())
                : Optional.empty();

        return holder.getDbFieldNames().isEmpty()
                // имена и типы данных для postgres (по строке заголовка)
                ? bookReader.getPostgresTypesByHeaderIndex(sheet, holder.getFirstDataRow(), 0, columnFrom, columnTo)
                // имена и типы данных для postgres (по списку имен полей)
                : bookReader.getPostgresTypesByFieldNames(sheet, holder.getFirstDataRow(), holder.getDbFieldNames());
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

    private List<String> toFieldTypes(Map<String, String> postgresTypes/*, QueryPropertyHolder holder*/) {
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

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class DatabaseWriterBuilder {
//        @NonNull
        private Connection connection;
        @NonNull
        private ExcelBookReader bookReader;
        private String schemeName = "public";
        private boolean overwrite = true;
        private ComponentLog logger;

        public DatabaseWriterBuilder connection(/*@NonNull*/ Connection connection) {
            this.connection = connection;
            return this;
        }

        public DatabaseWriterBuilder bookReader(@NonNull ExcelBookReader bookReader) {
            this.bookReader = bookReader;
            return this;
        }

        public DatabaseWriterBuilder schemeName(String schemeName) {
            if (!schemeName.isBlank())
                this.schemeName = schemeName;

            return this;
        }

        public DatabaseWriterBuilder overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }

        public DatabaseWriterBuilder logger(ComponentLog logger) {
            this.logger = logger;
            return this;
        }

        public DatabaseWriter build() {
            return new DatabaseWriter(connection, bookReader, schemeName, overwrite, logger);
        }
    }
}
