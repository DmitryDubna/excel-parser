package com.example.excelparser.utils.excel;

import lombok.Builder;
import lombok.ToString;

import java.util.List;

@Builder
@ToString
public class DbTablePropertyHolder {
    private String sheetName;
    private String dbTableName;
    private int firstDataRow;
    private List<String> dbColumnNames;
}
