package com.example.excelparser.utils.excel;

import lombok.Builder;
import lombok.Value;
import lombok.ToString;

import java.util.List;

@Builder
@Value
@ToString
public class QueryPropertyHolder {
    private String sheetName;
    private String dbTableName;
    private int firstDataRow;
    private List<String> dbColumnNames;
}
