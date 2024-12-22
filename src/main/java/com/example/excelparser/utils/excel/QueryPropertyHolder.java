package com.example.excelparser.utils.excel;

import lombok.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Builder
@Value
@ToString
public class QueryPropertyHolder {
    private String sheetName;
    private String dbTableName;
    private int headerRow;
    private int firstDataRow;
    private List<String> dbFieldNames;
    @Builder.Default
    private Optional<DataColumnInfo> dataColumnInfo = Optional.empty();
    @Builder.Default
    private Optional<Integer> lastDataRow = Optional.empty();

    @Value
    @ToString
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class DataColumnInfo {
        private int from;
        private int to;

        public static DataColumnInfo parse(String rangeString) {
            try {
                List<Integer> values = StringUtils.toStringList(rangeString, "-")
                        .stream()
                        .map(s -> Integer.valueOf(s) - 1)
                        .collect(Collectors.toList());
                return (values.size() < 2)
                        ? null
                        : new DataColumnInfo(values.get(0), values.get(1));
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
