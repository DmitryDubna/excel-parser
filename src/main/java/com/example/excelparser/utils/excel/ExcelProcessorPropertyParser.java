package com.example.excelparser.utils.excel;

import com.ibm.icu.text.Transliterator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@AllArgsConstructor
@ToString
public class ExcelProcessorPropertyParser {
    @ToString.Exclude
    private static final Transliterator TRANSLITERATOR = Transliterator.getInstance("Russian-Latin/BGN");
    @ToString.Exclude
    private final int DEFAULT_FIRST_DATA_ROW = 1;
    private List<String> sheetNames;
    private List<String> dbTableNames;
    private List<Integer> firstDataRows;
    private List<List<String>> dbColumnNames;

    public static ExcelProcessorPropertyParserBuilder builder() {
        return new ExcelProcessorPropertyParserBuilder();
    }

    public List<QueryPropertyHolder> buildQueryPropertyHolders() {
        var result = new ArrayList<QueryPropertyHolder>();
        for (int i = 0; i < sheetNames.size(); i++) {
            String sheetName = sheetNames.get(i);
            QueryPropertyHolder propertyHolder = QueryPropertyHolder.builder()
                    .sheetName(sheetName)
                    .dbTableName(
                            (dbTableNames.size() > i)
                                    ? dbTableNames.get(i)
                                    : TRANSLITERATOR.transliterate(sheetName).strip().replaceAll("\\W+", "_")
                    )
                    .firstDataRow((firstDataRows.size() > i) ? firstDataRows.get(i) : DEFAULT_FIRST_DATA_ROW)
                    .dbColumnNames((dbColumnNames.size() > i) ? dbColumnNames.get(i) : List.of())
                    .build();
            result.add(propertyHolder);
        }
        return result;
    }


    public static class ExcelProcessorPropertyParserBuilder {
        private List<String> sheetNames = new ArrayList<>();
        private List<String> dbTableNames = new ArrayList<>();
        private List<Integer> firstDataRows = new ArrayList<>();
        private List<List<String>> dbColumnNames = new ArrayList<>();

        ExcelProcessorPropertyParserBuilder() {
        }

        private List<String> toStringList(String s) {
            if (s.isBlank())
                return List.of();

            return Arrays.stream(s.split(","))
                    .map(String::trim)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        public ExcelProcessorPropertyParserBuilder sheetNames(String sheetNames) {
            this.sheetNames = toStringList(sheetNames);
            return this;
        }

        public ExcelProcessorPropertyParserBuilder dbTableNames(String dbTableNames) {
            this.dbTableNames = toStringList(dbTableNames);
            return this;
        }

        public ExcelProcessorPropertyParserBuilder firstDataRows(String firstDataRows) {
            this.firstDataRows = Arrays.stream(firstDataRows.split(","))
                    .map(String::trim)
                    .map(s -> Integer.valueOf(s) - 1)
                    .collect(Collectors.toList());
            return this;
        }

        public ExcelProcessorPropertyParserBuilder dbColumnNames(String dbColumnNames) {
            this.dbColumnNames = Arrays.stream(dbColumnNames.split(";"))
                    .map(row -> toStringList(row))
                    .collect(Collectors.toList());
            return this;
        }

        public ExcelProcessorPropertyParser build() {
            if (sheetNames.isEmpty())
                throw new RuntimeException("Список имен листов не должен быть пустым!");

            return new ExcelProcessorPropertyParser(sheetNames, dbTableNames, firstDataRows, dbColumnNames);
        }
    }
}
