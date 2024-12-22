package com.example.excelparser.utils.excel;

import com.ibm.icu.text.Transliterator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@ToString
public class ExcelProcessorPropertyParser {
    @ToString.Exclude
    private static final Transliterator TRANSLITERATOR = Transliterator.getInstance("Russian-Latin/BGN");
    @ToString.Exclude
    private final int DEFAULT_HEADER_ROW = 0;
    @ToString.Exclude
    private final int DEFAULT_FIRST_DATA_ROW = 1;
    private List<String> sheetNames;
    private List<String> dbTableNames;
    private List<Integer> headerRows;
    private List<Integer> firstDataRows;
    private List<List<String>> dbFieldNames;
    // расширенные данные
    private List<QueryPropertyHolder.DataColumnInfo> dataColumns;
    private List<Integer> lastDataRows;

    public enum ParseMode {
        ONE_TABLE_PER_SHEET,
        ALL_TABLES_PER_SHEET
    }

    public static ExcelProcessorPropertyParserBuilder builder() {
        return new ExcelProcessorPropertyParserBuilder();
    }

    public List<QueryPropertyHolder> buildQueryPropertyHolders(ParseMode mode) {
        return (ParseMode.ONE_TABLE_PER_SHEET == mode)
                ? parseOneTablePerSheetData()
                : parseAllTablesPerSheetData();
    }

    private List<QueryPropertyHolder> parseOneTablePerSheetData() {
        var result = new ArrayList<QueryPropertyHolder>();

        if (sheetNames.isEmpty())
            return result;

        for (int i = 0; i < sheetNames.size(); i++) {
            String sheetName = sheetNames.get(i);
            QueryPropertyHolder propertyHolder = QueryPropertyHolder.builder()
                    .sheetName(sheetName)
                    .dbTableName((dbTableNames.size() > i) ? dbTableNames.get(i) : transliterate(sheetName))
                    .dbFieldNames((dbFieldNames.size() > i) ? dbFieldNames.get(i) : List.of())
                    .headerRow((headerRows.size() > i) ? headerRows.get(i) : DEFAULT_HEADER_ROW)
                    .firstDataRow((firstDataRows.size() > i) ? firstDataRows.get(i) : DEFAULT_FIRST_DATA_ROW)
                    .lastDataRow((lastDataRows.size() > i) ? Optional.of(lastDataRows.get(i)) : Optional.empty())
                    .build();
            result.add(propertyHolder);
        }
        return result;
    }

    private List<QueryPropertyHolder> parseAllTablesPerSheetData() {
        var result = new ArrayList<QueryPropertyHolder>();

        if (sheetNames.isEmpty())
            return result;

        String sheetName = sheetNames.get(0);
        List<String> resultTableNames = handleDbTableNames(sheetNames, dbTableNames);

        for (int i = 0; i < resultTableNames.size(); i++) {
            QueryPropertyHolder propertyHolder = QueryPropertyHolder.builder()
                    .sheetName(sheetName)
                    .dbTableName(resultTableNames.get(i))
                    .dbFieldNames((dbFieldNames.size() > i) ? dbFieldNames.get(i) : List.of())
                    .headerRow((headerRows.size() > i) ? headerRows.get(i) : DEFAULT_HEADER_ROW)
                    .firstDataRow((firstDataRows.size() > i) ? firstDataRows.get(i) : DEFAULT_FIRST_DATA_ROW)
                    .lastDataRow((lastDataRows.size() > i) ? Optional.of(lastDataRows.get(i)) : Optional.empty())
                    .dataColumnInfo((dataColumns.size() > i) ? Optional.of(dataColumns.get(i)) : Optional.empty())
                    .build();
            result.add(propertyHolder);
        }
        return result;
    }

    private List<String> handleDbTableNames(List<String> sheetNames,
                                            List<String> dbTableNames) {
        if (!dbTableNames.isEmpty())
            return dbTableNames;

        return sheetNames.stream()
                .map(name -> transliterate(name))
                .collect(Collectors.toList());
    }

    private String transliterate(String s) {
        return TRANSLITERATOR.transliterate(s).strip().replaceAll("\\W+", "_");
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ExcelProcessorPropertyParserBuilder {
        private List<String> sheetNames = new ArrayList<>();
        private List<String> dbTableNames = new ArrayList<>();
        private List<Integer> headerRows = new ArrayList<>();
        private List<Integer> firstDataRows = new ArrayList<>();
        private List<List<String>> dbFieldNames = new ArrayList<>();
        // расширенные данные
        private List<QueryPropertyHolder.DataColumnInfo> dataColumns = new ArrayList<>();
        private List<Integer> lastDataRows = new ArrayList<>();

        public ExcelProcessorPropertyParserBuilder sheetNames(final String sheetNames) {
            this.sheetNames = StringUtils.toStringList(sheetNames, ",");
            return this;
        }

        public ExcelProcessorPropertyParserBuilder dbTableNames(final String dbTableNames) {
            this.dbTableNames = StringUtils.toStringList(dbTableNames, ",");
            return this;
        }

        public ExcelProcessorPropertyParserBuilder headerRows(final String headerRows) {
            this.headerRows = StringUtils.toIntList(headerRows, ",", s -> Integer.valueOf(s) - 1);
            return this;
        }

        public ExcelProcessorPropertyParserBuilder firstDataRows(final String firstDataRows) {
            this.firstDataRows = StringUtils.toIntList(firstDataRows, ",", s -> Integer.valueOf(s) - 1);
            return this;
        }

        public ExcelProcessorPropertyParserBuilder dbFieldNames(final String dbFieldNames) {
            this.dbFieldNames = Arrays.stream(dbFieldNames.split(";"))
                    .map(row -> StringUtils.toStringList(row, ","))
                    .collect(Collectors.toList());
            return this;
        }

        public ExcelProcessorPropertyParserBuilder dataColumns (final String dataColumns) {
            List<String> strings = StringUtils.toStringList(dataColumns, ",");
            if (strings.isEmpty())
                return this;

            this.dataColumns = strings.stream()
                    .map(QueryPropertyHolder.DataColumnInfo::parse)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return this;
        }

        public ExcelProcessorPropertyParserBuilder lastDataRows(final String lastDataRows) {
            this.lastDataRows = StringUtils.toIntList(lastDataRows, ",", s -> Integer.valueOf(s) - 1);
            return this;
        }

        public ExcelProcessorPropertyParser build() {
            if (sheetNames.isEmpty())
                throw new RuntimeException("Список имен листов не должен быть пустым!");

            return new ExcelProcessorPropertyParser(
                    sheetNames,
                    dbTableNames,
                    headerRows,
                    firstDataRows,
                    dbFieldNames,
                    dataColumns,
                    lastDataRows
            );
        }
    }
}
