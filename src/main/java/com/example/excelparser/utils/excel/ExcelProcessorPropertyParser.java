package com.example.excelparser.utils.excel;

import com.ibm.icu.text.Transliterator;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ToString
public class ExcelProcessorPropertyParser {
    private static final Transliterator TRANSLITERATOR = Transliterator.getInstance("Russian-Latin/BGN");
    private final int DEFAULT_FIRST_DATA_ROW = 2;
    private List<String> sheetNames;
    private List<String> dbTableNames;
    private List<Integer> firstDataRows;
    private List<List<String>> dbColumnNames;

    ExcelProcessorPropertyParser(List<String> sheetNames,
                                 List<String> dbTableNames,
                                 List<Integer> firstDataRows,
                                 List<List<String>> dbColumnNames) {
        this.sheetNames = sheetNames;
        this.dbTableNames = dbTableNames;
        this.firstDataRows = firstDataRows;
        this.dbColumnNames = dbColumnNames;
    }

    public static ExcelProcessorPropertyParserBuilder builder() {
        return new ExcelProcessorPropertyParserBuilder();
    }

    public List<DbTablePropertyHolder> buildPropertyHolders() {
        var result = new ArrayList<DbTablePropertyHolder>();
        for (int i = 0; i < sheetNames.size(); i++) {
            String sheetName = sheetNames.get(i);
//            String dbTableName = (dbTableNames.size() > i)
//                    ? dbTableNames.get(i)
//                    : TRANSLITERATOR.transliterate(sheetName).strip().replaceAll("\\W+", "_");
//            List<String> dbCoumnNames = (dbColumnNames.size() > i) ? dbColumnNames.get(i) : List.of();

            DbTablePropertyHolder propertyHolder = DbTablePropertyHolder.builder()
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
        private List<String> sheetNames;
        private List<String> dbTableNames;
        private List<Integer> firstDataRows;
        private List<List<String>> dbColumnNames;

        ExcelProcessorPropertyParserBuilder() {
        }

        public ExcelProcessorPropertyParserBuilder sheetNames(String sheetNames) {
            this.sheetNames = Arrays.stream(sheetNames.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
            return this;
        }

        public ExcelProcessorPropertyParserBuilder dbTableNames(String dbTableNames) {
            this.dbTableNames = Arrays.stream(dbTableNames.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
            return this;
        }

        public ExcelProcessorPropertyParserBuilder firstDataRows(String firstDataRows) {
            this.firstDataRows = Arrays.stream(firstDataRows.split(","))
                    .map(String::trim)
                    .map(Integer::valueOf)
                    .collect(Collectors.toList());
            return this;
        }

        public ExcelProcessorPropertyParserBuilder dbColumnNames(String dbColumnNames) {
            this.dbColumnNames = Arrays.stream(dbColumnNames.split(";"))
                    .map(row -> Arrays.stream(row.split(","))
                            .map(String::trim)
                            .collect(Collectors.toList())
                    )
                    .collect(Collectors.toList());
            return this;
        }

        public ExcelProcessorPropertyParser build() {
            return new ExcelProcessorPropertyParser(sheetNames, dbTableNames, firstDataRows, dbColumnNames);
        }
    }
}
