package com.example.excelparser.utils.excel;

import com.example.excelparser.utils.database.PostgresQueryService;
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

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DatabaseWriter {
    @NonNull
    private Connection connection;
    @NonNull
    private ExcelBookReader bookReader;
    private String schemeName;
    private boolean overwrite;
//    @NonNull
    private ComponentLog logger;
    int rowsPerBatch;

    private void logInfo(String msg) {
        System.out.println(msg);
    }

    public static DatabaseWriterBuilder builder() {
        return new DatabaseWriterBuilder();
    }

    public void write(List<QueryPropertyHolder> queryPropertyHolders) throws RuntimeException {
        queryPropertyHolders.forEach(this::write);
    }

    public void write(QueryPropertyHolder holder) throws RuntimeException {
        Sheet sheet = bookReader.getWorkbook().getSheet(holder.getSheetName());
        String tableName = holder.getDbTableName();

        Map<String, String> postgresTypes = preparePostgresTypes(sheet, holder);
        var databaseService = new PostgresQueryService(connection, schemeName, tableName, logger);
        tryCreateTable(databaseService, postgresTypes);

        if (overwrite)
            truncateTable(databaseService);

        writeData(databaseService, sheet, holder, postgresTypes);
    }

    private Map<String, String> preparePostgresTypes(final Sheet sheet,
                                                     final QueryPropertyHolder holder) throws RuntimeException {
        logInfo("Формирование типов данных Postgres");
        // формирование типов данных Postgres
        Map<String, String> postgresTypes = getPostgresTypes(sheet, holder);
        if (postgresTypes.isEmpty()) {
            throw new RuntimeException("Не удалось сформировать список типов данных");
        }
        logInfo("Типы данных Postgres успешно сформированы:\n" + postgresTypes);

        return postgresTypes;
    }

    private void tryCreateTable(final PostgresQueryService queryService,
                                final Map<String, String> postgresTypes) throws RuntimeException {
        logInfo("Подготовка таблицы БД к наполнению");
        // попытка создания таблицы
        if (!queryService.tryCreateTable(postgresTypes)) {
            throw new RuntimeException("Не удалось создать таблицу БД");
        }
        logInfo("Таблица БД готова к наполнению");
    }

    private void truncateTable(final PostgresQueryService queryService) throws RuntimeException {
        logInfo("Очистка таблицы БД");
        if (!queryService.truncateTable())
            throw new RuntimeException("Не удалось очистить таблицу БД");

        logInfo("Таблица БД успешно очищена");
    }

    private void writeData(final PostgresQueryService queryService,
                           final Sheet sheet,
                           final QueryPropertyHolder holder,
                           final Map<String, String> postgresTypes) throws RuntimeException {
        logInfo("Подготовка данных для записи в БД");
        // имена полей
        String fieldNames = PostgresQueryService.toFieldNames(postgresTypes);

        int rowFrom = holder.getFirstDataRow();
        // убеждаемся, что заданный индекс последней строки данных не выходит за рамки данных листа
        int lastDataRow = Math.min(
                holder.getLastDataRow().orElse(sheet.getLastRowNum()),
                sheet.getLastRowNum()
        );
        // значения полей
        while (rowFrom <= lastDataRow) {
            // убеждаемся, что при обработке пачки данных не вышли за индекс последней строки данных
            int rowTo = Math.min(rowFrom + rowsPerBatch - 1, lastDataRow);
            String indexRangeString = "[%d, %d]".formatted(rowFrom, rowTo);

            logInfo("Подготовка порции данных %s для записи в БД.".formatted(indexRangeString));
            // формирование строки значений полей
            Optional<String> fieldValues = toFieldValues(sheet, postgresTypes, holder, rowFrom, rowTo);

            if (fieldValues.isEmpty())
                throw new RuntimeException("Не удалось сформировать список значений полей данных из диапазона строк %s.".formatted(indexRangeString));

            logInfo("Запись порции данных %s в БД".formatted(indexRangeString));
            // заполнение таблицы
            if (!queryService.insertData(fieldNames, fieldValues.get())) {
                throw new RuntimeException("Не удалось записать порцию данных %s в БД".formatted(indexRangeString));
            }

            rowFrom += rowsPerBatch;
        }

        logInfo("Данные успешно записаны в БД");
    }

    private Optional<String> toFieldValues(Sheet sheet,
                                           Map<String, String> postgresTypes,
                                           QueryPropertyHolder holder,
                                           int rowFromIndex,
                                           int rowToIndex) {
        // типы полей
        List<String> fieldTypes = PostgresQueryService.toFieldTypes(postgresTypes);
        // значения полей
        QueryPropertyHolder.DataColumnInfo columnInfo = holder.getDataColumnInfo().orElse(null);
        return bookReader.toPostgresTableValues(
                sheet,
                fieldTypes,
                rowFromIndex,
                Optional.of(rowToIndex),
                Objects.nonNull(columnInfo) ? Optional.of(columnInfo.getFrom()) : Optional.empty(),
                Objects.nonNull(columnInfo) ? Optional.of(columnInfo.getTo()) : Optional.empty()
        );
    }

    private Map<String, String> getPostgresTypes(Sheet sheet, QueryPropertyHolder holder) {
        QueryPropertyHolder.DataColumnInfo columnInfo = holder.getDataColumnInfo().orElse(null);

        Optional<Integer> columnFrom = Objects.nonNull(columnInfo)
                ? Optional.of(columnInfo.getFrom())
                : Optional.empty();
        Optional<Integer> columnTo = Objects.nonNull(columnInfo)
                ? Optional.of(columnInfo.getTo())
                : Optional.empty();

        // если имена полей не заданы, для формирования имен полей используется заголовок таблицы (транслитерация)
        return holder.getDbFieldNames().isEmpty()
                // имена и типы данных для postgres (по строке заголовка)
                ? bookReader.getPostgresTypesByHeaderIndex(sheet, holder.getFirstDataRow(), holder.getHeaderRow(), columnFrom, columnTo)
                // имена и типы данных для postgres (по списку имен полей)
                : bookReader.getPostgresTypesByFieldNames(sheet, holder.getFirstDataRow(), holder.getDbFieldNames(), columnFrom, columnTo);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class DatabaseWriterBuilder {
        @NonNull
        private Connection connection;
        @NonNull
        private ExcelBookReader bookReader;
        private String schemeName = "public";
        private boolean overwrite = true;
        private ComponentLog logger;
        private int rowsPerBatch = 1000;

        public DatabaseWriterBuilder connection(@NonNull Connection connection) {
            this.connection = connection;
            return this;
        }

        public DatabaseWriterBuilder bookReader(@NonNull ExcelBookReader bookReader) {
            this.bookReader = bookReader;
            return this;
        }

        public DatabaseWriterBuilder schemeName(String schemeName) {
            if (!schemeName.isBlank())
                this.schemeName = schemeName.toLowerCase();

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

        public DatabaseWriterBuilder rowsPerBatch(int rowsPerBatch) {
            this.rowsPerBatch = rowsPerBatch;
            return this;
        }

        public DatabaseWriter build() {
            return new DatabaseWriter(connection, bookReader, schemeName, overwrite, logger, rowsPerBatch);
        }
    }
}
