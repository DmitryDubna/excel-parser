package com.example.excelparser.utils.excel;

import com.ibm.icu.text.Transliterator;
import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ValueRange;
import java.util.*;
import java.util.stream.Collectors;

public class ExcelBookReader {
    private static final Transliterator TRANSLITERATOR = Transliterator.getInstance("Russian-Latin/BGN");
    @Getter
    private final XSSFWorkbook workbook;
    private final FormulaEvaluator formulaEvaluator;

    {
        IOUtils.setByteArrayMaxOverride(1000000000);
    }

    public ExcelBookReader(InputStream inputStream) throws IOException {
        workbook = new XSSFWorkbook(inputStream);
        formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
    }

    public Optional<String> getFirstSheetName() {
        return (workbook.getNumberOfSheets() > 0)
                ? Optional.of(workbook.getSheetAt(0).getSheetName())
                : Optional.empty();
    }

    private boolean checkRowIndexValid(Sheet sheet, int rowIndex) {
        return ValueRange.of(sheet.getFirstRowNum(), sheet.getLastRowNum()).isValidValue(rowIndex);
    }

    /// Формирует коллекцию типов данных по индексу строки заголовка
    public Map<String, String> getPostgresTypesByHeaderIndex(Sheet sheet,
                                                             int dataRow,
                                                             int headerRow) {
        if (!checkRowIndexValid(sheet, dataRow) || ! checkRowIndexValid(sheet, headerRow))
            return Map.of();

        Row row = sheet.getRow(headerRow);
        return getPostgresTypesByHeaderIndex(sheet, dataRow, headerRow, row.getFirstCellNum(), row.getLastCellNum());
    }


    public Map<String, String> getPostgresTypesByHeaderIndex(Sheet sheet,
                                                             int dataRow,
                                                             int headerRow,
                                                             int columnFrom,
                                                             int columnTo) {
        var result = new LinkedHashMap<String, String>();

        if (!checkRowIndexValid(sheet, dataRow) || ! checkRowIndexValid(sheet, headerRow))
            return Map.of();


        Row row = sheet.getRow(headerRow);
        int indexFrom = (columnFrom >= row.getFirstCellNum()) ? columnFrom : row.getFirstCellNum();
        int indexTo = (columnTo < row.getLastCellNum()) ? columnTo : row.getLastCellNum() - 1;

        for (int i = indexFrom; i <= indexTo; i++) {
            Cell cell = row.getCell(i);
            int column = cell.getAddress().getColumn();
            String fieldName = TRANSLITERATOR
                    .transliterate(cell.getStringCellValue())
                    .strip()
                    .replaceAll("\\W+", "_");
            String fieldType = toPostgresType(sheet.getRow(dataRow).getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
            result.put(fieldName, fieldType);
        };
        return result;
    }

    /// Формирует коллекцию типов данных по списку имен колонок
    public Map<String, String> getPostgresTypesByFieldNames(Sheet sheet,
                                                            int dataRow,
                                                            List<String> fieldNames) {
        return getPostgresTypesByFieldNames(sheet, dataRow, fieldNames, 0, fieldNames.size() - 1);
    }

    public Map<String, String> getPostgresTypesByFieldNames(Sheet sheet,
                                                            int dataRow,
                                                            List<String> fieldNames,
                                                            int columnFrom,
                                                            int columnTo) {
        var result = new LinkedHashMap<String, String>();

        int indexFrom = (columnFrom >= 0) ? columnFrom : 0;
        int indexTo = (columnTo < fieldNames.size()) ? columnTo : fieldNames.size() - 1;

        for (int i = indexFrom; i <= indexTo; i++) {
            String fieldName = fieldNames.get(i)
                    .strip()
                    .replaceAll("\\W+", "_");;
            String fieldType = toPostgresType(sheet.getRow(dataRow).getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
            result.put(fieldName, fieldType);
        }
        return result;
    }

    private String toPostgresType(Cell cell) {
        switch (cell.getCellType()) {
            case NUMERIC -> {
                return DateUtil.isCellDateFormatted(cell) ? "TIMESTAMP" : "DOUBLE PRECISION";
            }
            case BOOLEAN -> {
                return "BOOLEAN";
            }
            default -> {
                return "TEXT";
            }
        }
    }

    public String toPostgresTableValues(Sheet sheet,
                                        List<String> fieldTypes,
                                        int rowFrom) {
        return toPostgresTableValues(sheet, fieldTypes, rowFrom, Optional.empty(), Optional.empty(), Optional.empty());
//                sheet.getLastRowNum(), 0, fieldTypes.size() - 1);
    }

    public String toPostgresTableValues(Sheet sheet,
                                        List<String> fieldTypes,
                                        int rowFrom,
                                        Optional<Integer> rowTo,
                                        Optional<Integer> columnFrom,
                                        Optional<Integer> columnTo) {
        var result = new ArrayList<String>();

        int rowToValue = rowTo.orElseGet(() -> sheet.getLastRowNum());

        int rowFromIndex = (rowFrom >= sheet.getFirstRowNum()) ? rowFrom : sheet.getFirstRowNum();
        int rowToIndex = (rowToValue <= sheet.getLastRowNum()) ? rowToValue : sheet.getLastRowNum();
//        int rowToIndex = (rowTo <= sheet.getLastRowNum()) ? rowTo : sheet.getLastRowNum();

        int columnFromIndex = columnFrom.orElse(0);
        int columnToIndex = columnTo.orElseGet(() -> fieldTypes.size() - 1);

        for (int i = rowFromIndex; i <= rowToIndex; i++) {
            String rowValues = toPostgresRowValues(sheet.getRow(i), fieldTypes, columnFromIndex, columnToIndex);
            result.add(rowValues);
        }
        return String.join(", ", result);
    }

    private String toPostgresRowValues(Row row,
                                       List<String> fieldTypes,
                                       int columnFrom,
                                       int columnTo) {
        var values = new ArrayList<String>();

        for (int i = 0; i < fieldTypes.size(); i++) {
            int columnIndex = columnFrom + i;
            if (columnIndex > columnTo)
                break;

            Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            values.add(Objects.isNull(cell) ? "null" : toPostgresString(cell, fieldTypes.get(i)));
        }
        return values.stream().collect(Collectors.joining(", ", "(", ")"));
    }

    private String toPostgresString(Cell cell, String fieldType) {
        return Optional.ofNullable(toValue(cell, fieldType))
                .map(value -> "'%s'".formatted(value))
                .orElse("null");
    }

    private String toValue(Cell cell, String fieldType) {
        switch (cell.getCellType()) {
            case NUMERIC -> {
                // дата
                if (DateUtil.isCellDateFormatted(cell)) {
                    return (fieldType.equals("TIMESTAMP"))
                            ? cell.getLocalDateTimeCellValue().format(DateTimeFormatter.ISO_DATE)
                            : null;
                }
                // число
                return (fieldType.equals("DOUBLE PRECISION"))
                        ? Double.valueOf(cell.getNumericCellValue()).toString()
                        : null;
            }
            case BOOLEAN -> {
                return (fieldType.equals("BOOLEAN"))
                        ? Boolean.toString(cell.getBooleanCellValue())
                        : null;
            }
            case FORMULA -> {
                Optional<Object> value = evaluateFormula(cell);
                return value.map(String::valueOf).orElse("null");
            }
            case STRING -> {
                return (fieldType.equals("TEXT"))
                        ? cell.getStringCellValue()
                        : null;
            }
            default -> {
                return null;
            }
        }
    }

    private Optional<Object> evaluateFormula(Cell cell) {
        CellType cellType = formulaEvaluator.evaluateFormulaCell(cell);

        switch (cellType) {
            case NUMERIC -> {
                return Optional.of(cell.getNumericCellValue());
            }
            case STRING -> {
                return Optional.ofNullable(cell.getStringCellValue());
            }
            case BOOLEAN -> {
                return Optional.of(cell.getBooleanCellValue());
            }
            default -> {
                break;
            }
        }
        return Optional.empty();
    }
}
