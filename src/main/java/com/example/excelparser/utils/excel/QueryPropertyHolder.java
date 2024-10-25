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
    private int firstDataRow;
    private List<String> dbFieldNames;
    @Builder.Default
    private Optional<DataColumnInfo> dataColumnInfo = Optional.empty();
    @Builder.Default
    private Optional<Integer> lastDataRows = Optional.empty();

    @ToString
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class DataColumnInfo {
        private int from;
        private int to;

        public static DataColumnInfo parse(String s) {
            try {
                List<Integer> values = StringUtils.toStringList(s, "-")
                        .stream()
                        .map(Integer::valueOf)
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

    public int getHeaderRow() {
        return (this.firstDataRow > 0) ? this.firstDataRow - 1 : 0;
    }
}
